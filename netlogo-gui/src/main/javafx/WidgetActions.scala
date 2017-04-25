// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ api, internalapi, nvm },
    internalapi.{ CompiledWidget, JobErrored, JobScheduler, ModelUpdate },
    nvm.{ SuspendableJob, Workspace }

import
  scala.collection.mutable.WeakHashMap

// NOTE: This class isn't specific to javafx, but seemed like the most convenient place to put it.
// The functionality of this package might belong more properly in "workspace" as it is currently understood.
// I'm hesitant to include it in workspace for several reasons:
//
// 1) It isn't fundamentally a concern of NetLogo code in general as it is primarily about operation management
//    and scheduling (which NetLogo operations cannot do).
// 2) Workspace already has enough cruft growing on it and anything which gets put into org.nlogo.workspace seems
//    like it may eventually end up added to Workspace.
class WidgetActions(workspace: Workspace, scheduler: JobScheduler) {
  var widgetsByJobTag = WeakHashMap.empty[String, CompiledWidget]

  def run(button: CompiledButton, interval: Long): Unit = {
    if (button.taskTag.isEmpty) {
      val job =
        new SuspendableJob(workspace.world.observers, button.widget.forever, button.procedure, 0, null, workspace.world.mainRNG)
      val task = scheduler.createJob(job, interval)
      button.taskTag = Some(task.tag)
      widgetsByJobTag(task.tag) = button
      button.isRunning.set(true)
      scheduler.queueTask(task)
    } else {
      throw new IllegalStateException("This job is already running")
    }
  }

  def notifyUpdate(update: ModelUpdate): Unit = {
    update match {
      case JobErrored(tag, error) =>
        widgetsByJobTag.get(tag) match {
          case Some(c: CompiledButton) =>
            c.taskTag = None
            c.isRunning.set(false)
            c.errored(error)
          case _ =>
        }
        widgetsByJobTag -= tag
      case other => widgetsByJobTag -= other.tag
    }
  }
}
