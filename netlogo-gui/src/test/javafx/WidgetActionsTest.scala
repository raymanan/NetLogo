// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  java.util.{ ArrayList => JArrayList }

import
  org.nlogo.{ compile, core, internalapi, job, nvm },
    core.{ Button, Femto },
    internalapi.{ ModelUpdate, TicksCleared, TicksStarted },
    nvm.{ Command, DummyWorkspace, Procedure },
    compile.{ api, NvmTests },
      api.StatementsBuilder,
      NvmTests.assembleProcedure,
    job.ScheduledJobThreadTest.SimpleScheduler

import
  org.scalatest.FunSuite

import
  scala.collection.JavaConverters.collectionAsScalaIterableConverter

class WidgetActionsTest extends FunSuite {
  trait Helper {
    val ws = new DummyWorkspace()
    def procedure(b: StatementsBuilder): Procedure = {
      import org.nlogo.core.{ SourceLocation, Token, TokenType }
      val p = new Procedure(false, "ABC", Token("ABC", TokenType.Ident, "ABC")(SourceLocation(0, 5, "")), Seq(), null)
      p.topLevel = true
      val stmts = new StatementsBuilder()
      assembleProcedure(p, b)
      p
    }
    val dummyProcedure = procedure(new StatementsBuilder() { done })
    val errorProcedure = procedure(new StatementsBuilder() {})
    val scheduler = new SimpleScheduler()
    val actions = new WidgetActions(ws, scheduler)
    var error: Exception = null
    val button = new CompiledButton(Button(Some(""), 0, 0, 0, 0), None, "abc", dummyProcedure, actions)
    button.isRunning.onError(err => error = err)
    val errorButton = new CompiledButton(Button(Some(""), 0, 0, 0, 0), None, "abc", errorProcedure, actions)
    errorButton.isRunning.onError(err => error = err)
    def runTasks() {
      while (! scheduler.queue.isEmpty) {
        stepTasks()
      }
    }
    def runTasks(i: Int): Unit = {
      for (j <- 0 until i) { stepTasks }
    }
    private def stepTasks(): Unit = {
      scheduler.stepTask()
      val updates = new JArrayList[ModelUpdate]()
      scheduler.updates.drainTo(updates)
      updates.asScala.foreach { update => actions.notifyUpdate(update) }
    }
  }

  test("WidgetActions.run(button) starts that button running") { new Helper {
    button.start()
    assert(button.isRunning.currentValue)
    assert(! scheduler.queue.isEmpty, "run did not schedule job to queue")
  } }

  test("WidgetActions.run(button) raises an exception if the button is already running") { new Helper {
    button.start()
    intercept[IllegalStateException] { button.start() }
  } }

  test("WidgetActions causes button to stop when the button halts or is finished") { new Helper {
    button.start()
    runTasks()
    assert(! button.isRunning.currentValue)
    assert(error == null, "button errored while running")
  } }

  test("WidgetActions causes the button to have an error when the button procedure errors") { new Helper {
    errorButton.start()
    runTasks()
    assert(error != null, "error was not sent to onError callback")
  } }

  test("WidgetActions.stop(button) stops that button if it is running") { new Helper {
    errorButton.start()
    errorButton.stop()
    runTasks(3)
    assert(! button.isRunning.currentValue)
    assert(error == null) // show that the error job was never run
  } }

  test("WidgetActions.stop(button) raises an exception if the button is not running") { new Helper {
    intercept[IllegalStateException] { button.stop() }
  } }

  test("TicksStarted messages update each CompiledButton to set ticks to enabled") { new Helper {
    button.modelLoaded()
    actions.notifyUpdate(TicksStarted)
    assert(button.ticksEnabled.currentValue)
  } }

  test("TicksCleared messages update each CompiledButton to set ticksEnabled to false") { new Helper {
    assert(! button.ticksEnabled.currentValue)
    button.modelLoaded()
    actions.notifyUpdate(TicksStarted)
    actions.notifyUpdate(TicksCleared)
    assert(! button.ticksEnabled.currentValue)
  } }

  test("WidgetActions.addMonitor registers a monitor with the job thread") { new Helper {
    pending
  } }

  test("MonitorsUpdate messages update each of the monitors mentioned") { new Helper {
    pending
  } }
}
