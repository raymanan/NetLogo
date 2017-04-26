// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ core, internalapi, nvm },
    core.{ Button => CoreButton, CompilerException },
    internalapi.{ CompiledButton => ApiCompiledButton },
    nvm.Procedure

case class CompiledButton(
  val widget:        CoreButton,
  val compilerError: Option[CompilerException],
  val procedureTag:  String,
  val procedure:     Procedure,
  widgetActions:     WidgetActions)
  extends ApiCompiledButton {
    var taskTag: Option[String] = None
    val isRunning    = new JobRunningMonitorable
    val ticksEnabled = new TicksStartedMonitorable
    def start(interval: Long = 0): Unit = {
      widgetActions.run(this, interval)
    }
    def stop(): Unit = {
      widgetActions.stop(this)
    }
    def errored(e: Exception): Unit = isRunning.errorCallback(e)
  }
