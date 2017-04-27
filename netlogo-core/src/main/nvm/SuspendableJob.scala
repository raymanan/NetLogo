// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.nvm

import org.nlogo.agent.{ AgentIterator, AgentSet }
import org.nlogo.internalapi.JobScheduler
import org.nlogo.api.{ JobOwner, MersenneTwisterFast }

class DummyJobOwner(val random: MersenneTwisterFast) extends JobOwner {
  def displayName: String = "Job Owner" // TODO: we may want another button
  def isButton: Boolean = true // TODO: our only owners at this point are buttons
  def isCommandCenter: Boolean = false
  def isLinkForeverButton: Boolean = false
  def isTurtleForeverButton: Boolean = false
  def ownsPrimaryJobs: Boolean = true

  def classDisplayName: String = "Button"
  def headerSource: String = ""
  def innerSource: String = ""
  def innerSource_=(s: String): Unit = {}
  def kind: org.nlogo.core.AgentKind = org.nlogo.core.AgentKind.Observer
  def source: String = ""
}

// TODO: Note that the interaction between ScheduledJobThread and this class means it is possible to
// stop this job normally in such a way that the procedure *hasn't finished*.
// This should be fixed before public release.
class SuspendableJob(
  suspendedState: Option[(Context, AgentIterator)], parentActivation: Activation, forever: Boolean, agentset: AgentSet, topLevelProcedure: Procedure,
  address: Int, parentContext: Context, random: MersenneTwisterFast)
  extends Job(new DummyJobOwner(random), agentset, topLevelProcedure, address, parentContext, random)
  with org.nlogo.internalapi.SuspendableJob {

  def this(
  suspendedState: Option[(Context, AgentIterator)], forever: Boolean, agentset: AgentSet, topLevelProcedure: Procedure,
  address: Int, parentContext: Context, random: MersenneTwisterFast) =
    this(suspendedState, {
      // if the Job was created by Evaluator, then we may have no parent context - ST 7/11/06
      if (parentContext == null)
        new Activation(topLevelProcedure, null, address)
      else
        parentContext.activation
    }, forever, agentset, topLevelProcedure, address, parentContext, random)

  def this(agentset: AgentSet, topLevelProcedure: Procedure,
    address: Int, parentContext: Context, random: MersenneTwisterFast) =
      this(None, false, agentset, topLevelProcedure, address, parentContext, random)

  def this(agentset: AgentSet, forever: Boolean, topLevelProcedure: Procedure,
    address: Int, parentContext: Context, random: MersenneTwisterFast) =
      this(None, forever, agentset, topLevelProcedure, address, parentContext, random)

  var scheduler: JobScheduler = null

  def scheduledBy(s: JobScheduler) = {
    scheduler = s
  }

  override def exclusive = true

  def intact: Boolean = suspendedState.isEmpty

  // we are not suspendable. we run to the end and that's it
  override def step() { throw new UnsupportedOperationException() }

  def runFor(steps: Int): Option[SuspendableJob] = {
    state = Job.RUNNING
    // Note that this relies on shufflerators making a copy, which might change in a future
    // implementation. The cases where it matters are those where something happens that changes the
    // agentset as we're iterating through it, for example if we're iterating through all turtles
    // and one of them hatches; the hatched turtle must not be returned by the shufflerator.
    // - ST 12/5/05, 3/15/06
    (suspendedState match {
      case Some((ctx, agents)) => runForSteps(ctx, agents, steps)
      case None =>
        val it = agentset.shufflerator(random)
        runForSteps(generateContext(it), it, steps)
    }) match {
      case Left(s: SuspendableJob) => Some(s)
      case Right(false) if forever => Some(copy(None))
      case _                       =>
        state = Job.DONE
        None
    }
  }

  @scala.annotation.tailrec
  private def runForSteps(
    ctx:         Context,
    agents:      AgentIterator,
    targetSteps: Int): Either[SuspendableJob, Boolean] = {
    ctx.runFor(targetSteps) match {
      case Left(continueContext) => Left(copy(Some((ctx, agents))))
      case Right(completedSteps) =>
        if (ctx.activation == parentActivation)
          ctx.activation.binding = ctx.activation.binding.exitScope()
        if (agents.hasNext)
          runForSteps(generateContext(agents), agents, targetSteps - completedSteps)
        else Right(ctx.stopping)
    }
  }

  private def generateContext(agents: AgentIterator) = {
    val ctx = new Context(this, agents.next(), address, parentActivation)
    ctx.activation.binding = ctx.activation.binding.enterScope()
    ctx
  }

  def copy(suspendedState: Option[(Context, AgentIterator)]): SuspendableJob =
    new SuspendableJob(suspendedState, parentActivation, forever, agentset, topLevelProcedure, address, parentContext, random)

  // used by Evaluator.MyThunk
  def runResult() =
    new Context(this, agentset.iterator.next(), 0, null)
     .callReporterProcedure(new Activation(topLevelProcedure, null, 0))

  override def userHasHalted(): Boolean =
    Thread.currentThread.isInterrupted && scheduler.haltRequested
}
