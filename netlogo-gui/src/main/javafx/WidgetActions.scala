// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ internalapi, nvm },
    internalapi.JobScheduler,
    nvm.SuspendableJob

// NOTE: This class isn't specific to javafx, but seemed like the most convenient place to put it.
// The functionality of this package might belong more properly in "workspace" as it is currently understood.
// I'm hesitant to include it in workspace for several reasons:
//
// 1) It isn't fundamentally a concern of NetLogo code in general as it is primarily about operation management
//    and scheduling (which NetLogo operations cannot do).
// 2) Workspace already has enough cruft growing on it and anything which gets put into org.nlogo.workspace seems
//    like it may eventually end up added to Workspace.
class WidgetActions(scheduler: JobScheduler) {
  def run(button: CompiledButton, interval: Long): Unit = {
    if (button.taskTag.isEmpty) {
      val job =
        new SuspendableJob(null, button.widget.forever, button.procedure, 0, null, null)
      val task = scheduler.createJob(job, interval)
      button.taskTag = Some(task.tag)
      button.isRunning.set(true)
      scheduler.queueTask(task)
    } else {
      throw new IllegalStateException("This job is already running")
    }
  }
}
