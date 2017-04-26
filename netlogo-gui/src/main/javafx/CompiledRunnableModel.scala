// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import org.nlogo.internalapi.{
  CompiledModel, CompiledWidget, CompiledMonitor => ApiCompiledMonitor,
  CompiledSlider => ApiCompiledSlider, EmptyRunnableModel, AddProcedureRun,
  ModelAction, ModelUpdate, Monitorable, MonitorsUpdate,
  NonCompiledWidget, RunnableModel, RunComponent,
  SchedulerWorkspace, StopProcedure, TicksCleared, TicksStarted }

import org.nlogo.core.{ AgentKind, Chooser => CoreChooser,
  CompilerException, InputBox => CoreInputBox, Model, Monitor => CoreMonitor, NumericInput, Program,
  Slider => CoreSlider, StringInput, Switch => CoreSwitch, Widget }
import org.nlogo.nvm.{ CompilerResults, ConcurrentJob, ExclusiveJob, Procedure, SuspendableJob }
import org.nlogo.workspace.AbstractWorkspace

import scala.util.{ Failure, Success }

class CompiledRunnableModel(
  workspace: AbstractWorkspace with SchedulerWorkspace,
  compiledWidgets: Seq[CompiledWidget],
  widgetActions: WidgetActions) extends RunnableModel  {
  val jobThread = workspace.scheduledJobThread

  private var taggedComponents = Map.empty[String, RunComponent]

  val monitorRegistry: Map[String, ReporterMonitorable] =
    compiledWidgets.flatMap {
      case cm: CompiledMonitor => Seq(cm)
      case cs: CompiledSlider  =>
        Seq(cs.value, cs.min, cs.max, cs.inc).collect {
          case cm: CompiledMonitorable[Double] => cm
        }
      case _ => Seq()
    }.map {
      case um: ReporterMonitorable => um.procedureTag -> um
    }.toMap

  def modelLoaded(): Unit = {
    monitorRegistry.values.foreach {
      case um: ReporterMonitorable =>
        val job =
          new SuspendableJob(workspace.world.observers, false, um.procedure, 0, null, workspace.world.mainRNG)
        jobThread.registerMonitor(um.procedureTag, job)
    }
  }

  def modelUnloaded(): Unit = {
    // TODO: unload monitors, remove existing jobs here
  }

  private def registerTag(componentOpt: Option[RunComponent], action: ModelAction, tag: String): Unit = {
    componentOpt.foreach { component =>
      taggedComponents = taggedComponents + (tag -> component)
      component.tagAction(action, tag)
    }
  }

  def notifyUpdate(update: ModelUpdate): Unit = {
    // TODO: widgetActions needs to be notified of updates even after this class is simplified
    widgetActions.notifyUpdate(update)
    update match {
      case MonitorsUpdate(values, time) =>
        values.foreach {
          case (k, Success(v)) =>
            monitorRegistry.get(k).foreach(_.update(v))
          case (k, Failure(v)) =>
            println(s"failure for monitor ${monitorRegistry(k)}: $v")
            v.printStackTrace()
            // println(monitorRegistry.get(k).map(_.procedure.dump))
        }
      case TicksStarted =>
        compiledWidgets.foreach {
          case c: CompiledButton => c.ticksEnabled.set(true)
          case _ =>
        }
      case TicksCleared =>
        compiledWidgets.foreach {
          case c: CompiledButton => c.ticksEnabled.set(false)
          case _ =>
        }
      case other =>
        taggedComponents.get(update.tag).foreach(_.updateReceived(update))
        taggedComponents -= update.tag
    }
  }
}

case class CompiledMonitor(
  val widget:         CoreMonitor,
  val compilerError:  Option[CompilerException],
  val procedureTag:   String,
  val procedure:      Procedure,
  val compiledSource: String)
  extends ApiCompiledMonitor
  with ReporterMonitorable {
    var updateCallback: (String => Unit) = { (s: String) => }

    def onUpdate(callback: String => Unit): Unit = {
      updateCallback = callback
    }

    def onError(callback: Exception => Unit): Unit = {
      // TODO: Fill this out
    }

    def defaultValue = "0"

    var currentValue = defaultValue

    def update(value: AnyRef): Unit = {
      value match {
        case s: String =>
          currentValue = s
          updateCallback(s)
        case other     => updateCallback(other.toString)
      }
    }
}

case class NonCompiledMonitorable[A](val defaultValue: A) extends Monitorable[A] {
  val currentValue: A = defaultValue
  def onUpdate(callback: A => Unit): Unit = {}
  def onError(callback: Exception => Unit): Unit = {}
  def compilerError = None
  def procedureTag = ""
}

class TicksStartedMonitorable extends Monitorable[Boolean] {
  def defaultValue = false
  var currentValue = defaultValue

  var updateCallback: (Boolean => Unit) = { (a: Boolean) => }

  def onUpdate(callback: Boolean => Unit): Unit = {
    updateCallback = callback
  }

  /* this monitorable cannot error */
  def onError(callback: Exception => Unit): Unit = {}

  def set(b: Boolean): Unit = {
    currentValue = b
    updateCallback(b)
  }
}

class JobRunningMonitorable extends Monitorable[Boolean] {
  def defaultValue = false
  var currentValue = defaultValue

  var updateCallback: (Boolean => Unit) = { (a: Boolean) => }
  var errorCallback: (Exception => Unit) = { (e: Exception) => }

  def onUpdate(callback: Boolean => Unit): Unit = {
    updateCallback = callback
  }

  def onError(callback: Exception => Unit): Unit = {
    errorCallback = callback
  }

  def set(b: Boolean): Unit = {
    currentValue = b
    updateCallback(b)
  }
}

case class CompiledMonitorable[A](
  val defaultValue: A,
  val compilerError: Option[CompilerException],
  val procedureTag: String,
  val procedure: Procedure,
  val compiledSource: String)(implicit ct: scala.reflect.ClassTag[A])
  extends Monitorable[A]
  with ReporterMonitorable {

  var currentValue: A = defaultValue

  var updateCallback: (A => Unit) = { (a: A) => }

  def onUpdate(callback: A => Unit): Unit = {
    updateCallback = callback
  }

  def onError(callback: Exception => Unit): Unit = {
    // TODO: Fill this out
  }

  def update(value: AnyRef): Unit = {
    value match {
      case a: A  =>
        currentValue = a
        updateCallback(a)
      case other =>
    }
  }
}

case class CompiledSlider(
  val widget: CoreSlider,
  val value:  Monitorable[Double],
  val min:    Monitorable[Double],
  val max:    Monitorable[Double],
  val inc:    Monitorable[Double]) extends ApiCompiledSlider {

  def update(expected: AnyRef, update: AnyRef): Unit = {
    // TODO: Fill this out
  }
}
