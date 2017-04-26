// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ core, internalapi },
    core.{ Slider => CoreSlider },
    internalapi.{ CompiledSlider => ApiCompiledSlider, Monitorable }

case class CompiledSlider(
  val widget: CoreSlider,
  val value:  Monitorable[Double],
  val min:    Monitorable[Double],
  val max:    Monitorable[Double],
  val inc:    Monitorable[Double],
  widgetActions: WidgetActions) extends ApiCompiledSlider {

  def update(expected: AnyRef, update: AnyRef): Unit = {
    // TODO: Fill this out
  }

  override def modelLoaded(): Unit = {
    Seq(value, min, max, inc).foreach {
      case c: CompiledMonitorable[Double] => widgetActions.addMonitorable(c)

      case _ =>
    }
  }
}
