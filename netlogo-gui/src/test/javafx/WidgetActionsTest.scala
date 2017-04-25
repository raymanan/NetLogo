// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ core, job, nvm },
    core.Button,
    nvm.Procedure,
    job.ScheduledJobThreadTest.SimpleScheduler

import
  org.scalatest.FunSuite

class WidgetActionsTest extends FunSuite {
  trait Helper {
    val dummyProcedure = {
      import org.nlogo.core.{ SourceLocation, Token, TokenType }
      new Procedure(false, "ABC", Token("ABC", TokenType.Ident, "ABC")(SourceLocation(0, 5, "")), Seq(), null)
    }
    val scheduler = new SimpleScheduler()
    val actions = new WidgetActions(scheduler)
    val button = new CompiledButton(Button(Some(""), 0, 0, 0, 0), None, "abc", dummyProcedure, actions)
  }

  test("WidgetActions.run(button) starts that button running") { new Helper {
    button.start(0)
    assert(button.isRunning.currentValue)
    assert(! scheduler.queue.isEmpty, "run did not schedule job to queue")
  } }

  test("WidgetActions.run(button) raises an exception if the button is already running") { new Helper {
    button.start(0)
    intercept[IllegalStateException](button.start(0))
  } }

  test("WidgetActions causes button to stop when the button halts or is finished") {
    pending
  }

  test("WidgetActions causes the button to have an error when the button procedure errors") {
    pending
  }

  test("WidgetActions.stop(button) stops that button if it is running") {
    pending
  }

  test("WidgetActions.stop(button) raises an exception if the button is not running") {
    pending
  }
}
