// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.job

import java.util.{ ArrayList => JArrayList, Comparator, UUID }
import java.util.concurrent.{ BlockingQueue, PriorityBlockingQueue, TimeUnit }

import org.nlogo.api.HaltSignal
import org.nlogo.internalapi.{ AddProcedureRun, JobScheduler => ApiJobScheduler,
  JobDone, JobErrored, JobHalted, ModelAction, ModelUpdate, MonitorsUpdate, StopProcedure,
  SuspendableJob, UpdateInterfaceGlobal }

import scala.annotation.tailrec
import scala.util.{ Failure, Success, Try }
import scala.collection.JavaConverters._
import scala.collection.mutable.SortedSet
import scala.math.Ordering

object ScheduledJobThread {
  sealed trait ScheduledEvent {
    def submissionTime: Long
  }

  case class ScheduleOperation(op: () => Unit, tag: String, submissionTime: Long) extends ScheduledEvent
  case class StopJob(cancelTag: String, submissionTime: Long) extends ScheduledEvent
  // Q: Why not have AddJob and RunJob be the same?
  // A: I don't think that's a bad idea, per se, but I want to allow flexibility in the future.
  //    Having RunJob events created only by the Scheduler allows them
  //    to hold additional information about the job including things
  //    like "how long since it was run last" and "how long is it estimated to run for"
  //    that 't available when adding the job. RG 3/28/17
  case class AddJob(job: SuspendableJob, tag: String, interval: Long, submissionTime: Long) extends ScheduledEvent
  case class RunJob(job: SuspendableJob, tag: String, interval: Long, submissionTime: Long) extends ScheduledEvent
  case class AddMonitor(op: SuspendableJob, tag: String, submissionTime: Long) extends ScheduledEvent
  case class RunMonitors(monitorOps: Map[String, SuspendableJob], submissionTime: Long) extends ScheduledEvent

  object JobSuspension {
    implicit object SuspensionOrdering extends Ordering[JobSuspension] {
      def compare(x: JobSuspension, y: JobSuspension): Int = {
        if (x.timeUnsuspended < y.timeUnsuspended) -1
        else if (x.timeUnsuspended > y.timeUnsuspended) 1
        else 0
      }
    }
  }
  case class JobSuspension(interval: Long, event: ScheduledEvent) {
    val timeUnsuspended = event.submissionTime + interval
  }

  object PriorityOrder extends Comparator[ScheduledEvent] {
    // lower means "first" or higher priority
    def basePriority(e: ScheduledEvent): Int = {
      e match {
        case ScheduleOperation(_, _, _) => 0
        case StopJob(_, _)              => 1
        case AddJob(_, _, _, _)         => 2
        case AddMonitor(_, _, _)        => 2
        case RunJob(_, _, _, _)         => 3
        case RunMonitors(_, _)          => 3
      }
    }
    def compare(e1: ScheduledEvent, e2: ScheduledEvent): Int = {
      val p1 = basePriority(e1)
      val p2 = basePriority(e2)
      if (p1 < p2) -1
      else if (p1 > p2) 1
      else if (e1.submissionTime < e2.submissionTime) -1
      else if (e1.submissionTime > e2.submissionTime) 1
      else 0
    }
  }
}

import ScheduledJobThread._

trait JobScheduler extends ApiJobScheduler {
  val StepsPerRun = 1000
  val MonitorInterval = 100

  def timeout = 50

  def timeoutUnit: TimeUnit = TimeUnit.MILLISECONDS

  // queue contains *inbound* information about tasks the jobs queue is expected
  // to perform. This variable is written to by any thread and read by the job
  // thread. Note that the job thread itself writes to this frequently.
  def queue: BlockingQueue[ScheduledEvent]

  // updates contains *outbound* information about the state of the enqueued jobs
  // this variable is typically written to on the event thread and read elsewhere
  def updates: BlockingQueue[ModelUpdate]

  // suspended jobs is a sorted set of job suspensions. This is expected to only
  // be modified by the job thread (and is therefore private).
  // A scala collection was chosen for convenience reasons, a Java collection
  // might offer better performance.
  private val suspendedJobs = SortedSet.empty[JobSuspension]

  // haltRequested tracks whether the user has requested a halt.
  // It is only read from the job thread, but it written from any thread.
  @volatile var haltRequested: Boolean = false

  // NOTE: These are *not* volatile because it should only ever be accessed on the
  // background thread. Do *NOT* modify these from any other thread.
  //
  // We opted to use scala collections here because we think this will be empty most of the time
  // if we're ever in a situation where this is going to be non-empty or have more than a few
  // elements, consider using java collections instead
  private var stopList        = Set.empty[String]
  private var pendingMonitors = Map.empty[String, SuspendableJob]
  private var monitorsRunning = false
  private var monitorDataStale = true

  def currentTime: Long = System.currentTimeMillis

  def scheduleJob(job: SuspendableJob): String = {
    scheduleJob(job, 0)
  }

  def scheduleJob(job: SuspendableJob, interval: Long): String = {
    val jobTag = UUID.randomUUID.toString
    val e = AddJob(job, jobTag, interval, currentTime)
    queue.add(e)
    jobTag
  }

  def scheduleOperation(op: () => Unit): String = {
    val jobTag = UUID.randomUUID.toString
    val e = ScheduleOperation(op, jobTag, currentTime)
    queue.add(e)
    jobTag
  }

  def registerMonitor(name: String, op: SuspendableJob): Unit = {
    queue.add(AddMonitor(op, name, currentTime))
  }

  def stopJob(jobTag: String): Unit = {
    queue.add(StopJob(jobTag, currentTime))
  }

  // TODO: This doesn't yet return information about completed jobs
  def runEvent(): Unit = {
    queue.poll(timeout, timeoutUnit) match {
      case null                          => clearStopList()
      case RunJob(job, tag, interval, _) =>
        runJob(job, tag, interval)
      case ScheduleOperation(op, tag, _) => runOperation(op, tag)
      case AddMonitor(op, tag, _)        => addMonitor(tag, op)
      case StopJob(cancelTag, time)      => stopList += cancelTag
      case AddJob(job, tag, interval, _) if stopList.contains(tag) => jobStopped(tag)
      case AddJob(job, tag, interval, _) =>
        job.scheduledBy(this)
        queue.add(RunJob(job, tag, interval, currentTime))
      case RunMonitors(updaters, time)   =>
        pendingMonitors.values.foreach(_.scheduledBy(this))
        val allMonitors = updaters ++ pendingMonitors
        pendingMonitors = Map.empty[String, SuspendableJob]
        runMonitors(allMonitors)
    }
    rescheduleSuspendedJobs()
  }

  def halt(): Unit = {
    haltRequested = true
  }

  private def rescheduleSuspendedJobs(): Unit = {
    val toSchedule = suspendedJobs.takeWhile(_.timeUnsuspended < currentTime)
    toSchedule.foreach {
      case s@JobSuspension(i, e) =>
        queue.add(e)
        suspendedJobs -= s
    }
  }

  private def clearStopList(): Unit = {
    if (suspendedJobs.isEmpty)
      stopList.foreach(jobStopped)
    else
      suspendedJobs.foreach {
        case js@JobSuspension(_, RunJob(_, t, _, _)) if stopList.contains(t) =>
          suspendedJobs -= js
          jobStopped(t)
        case _ =>
      }
  }

  private def jobStopped(tag: String): Unit = {
    stopList -= tag
    updates.add(JobDone(tag))
  }

  private def runJob(job: SuspendableJob, tag: String, interval: Long): Unit = {
    monitorDataStale = true
    if (stopList.contains(tag)) jobStopped(tag)
    else {
      Try(job.runFor(StepsPerRun)) match {
        case Success(None)                => updates.add(JobDone(tag))
        case Success(Some(j))             =>
          val reRun = RunJob(j, tag, interval, currentTime)
          if (interval == 0)  queue.add(reRun)
          else                suspendedJobs += JobSuspension(interval, reRun)
        case Failure(e: RuntimeException with HaltSignal) => jobHalted(tag, e)
        case Failure(e: RuntimeException) => updates.add(JobErrored(tag, e))
        case Failure(e)                   => throw e
      }
    }
  }

  private def jobHalted(tag: String, e: RuntimeException with HaltSignal): Unit = {
    updates.add(JobHalted(tag))
    if (e.haltAll) {
      haltAllJobs()
    }
  }

  protected def haltAllJobs(): Unit = {
    def haltEvent(s: ScheduledEvent): Unit = {
      s match {
        case RunJob(_, t, _, _)         => updates.add(JobHalted(t))
        case AddJob(_, t, _, _)         => updates.add(JobHalted(t))
        case ScheduleOperation(_, t, _) => updates.add(JobHalted(t))
        case _ =>
      }
    }
    suspendedJobs.foreach(s => haltEvent(s.event))
    suspendedJobs.clear()
    val queuedTasks = new JArrayList[ScheduledEvent]()
    queue.drainTo(queuedTasks)
    queuedTasks.asScala.foreach(haltEvent _)
    haltRequested = false
    pendingMonitors = Map.empty[String, SuspendableJob]
  }

  private def runOperation(op: () => Unit, tag: String): Unit =  {
    monitorDataStale = true
    Try(op()) match {
      case Success(())                  => updates.add(JobDone(tag))
      case Failure(e: RuntimeException with HaltSignal) => jobHalted(tag, e)
      case Failure(e: RuntimeException) => updates.add(JobErrored(tag, e))
      case Failure(e)                   => throw e
    }
  }

  private def addMonitor(tag: String, op: SuspendableJob): Unit = {
    if (monitorsRunning) pendingMonitors += tag -> op
    else {
      queue.add(RunMonitors(Map(tag -> op), currentTime))
      monitorsRunning = true
    }
  }

  private def runMonitors(ops: Map[String, SuspendableJob]): Unit = {
    if (monitorDataStale) {
      val (monitorValues, halted) = runMonitorRec(ops, Map.empty[String, Try[AnyRef]])
      updates.add(MonitorsUpdate(monitorValues, currentTime))
      if (! halted) {
        queue.add(RunMonitors(ops, currentTime))
        monitorDataStale = false
      }
    } else {
      queue.add(RunMonitors(ops, currentTime + MonitorInterval))
    }
  }

  @tailrec
  private def runMonitorRec(ops: Map[String, SuspendableJob], acc: Map[String, Try[AnyRef]]): (Map[String, Try[AnyRef]], Boolean) = {
    if (ops.isEmpty) (acc, false)
    else {
      val (key, job) = ops.head
      Try(job.runResult()) match {
        case Failure(e: RuntimeException with HaltSignal) =>
          haltAllJobs()
          (acc, true)
        case other => runMonitorRec(ops.tail, acc + (key -> other))
      }
    }
  }
}

class ScheduledJobThread(val updates: BlockingQueue[ModelUpdate])
  extends Thread(null, null, "ScheduledJobThread", JobThread.stackSize * 1024 * 1024)
  with JobScheduler {

  val queue = new PriorityBlockingQueue[ScheduledEvent](100, ScheduledJobThread.PriorityOrder)

  @volatile private var dying = false

  setPriority(Thread.NORM_PRIORITY - 1)
  start()

  override def run(): Unit = {
    while (! dying) {
      try {
        runEvent()
      } catch {
        case i: InterruptedException =>
        case e: Exception =>
          println(e)
          e.printStackTrace()
      }
      if (haltRequested) {
        haltAllJobs()
      }
    }
  }

  override def halt(): Unit = {
    if (! haltRequested) {
      super.halt()
      interrupt()
    }
  }

  def die(): Unit = {
    dying = true
    haltRequested = true
    interrupt()
    join()
  }
}
