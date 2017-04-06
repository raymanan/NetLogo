// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo(UTF8)

package org.nlogo.javafx

import javafx.fxml.FXML
import javafx.event.{ ActionEvent, EventHandler }
import javafx.scene.control.{ Button, ScrollPane, Slider }
import javafx.scene.layout.Pane
import javafx.stage.{ FileChooser, Window }

import org.nlogo.core.{
  I18N, Model, Button => CoreButton, Chooser => CoreChooser, InputBox => CoreInputBox,
  Monitor => CoreMonitor, Output => CoreOutput, Plot => CorePlot, Slider => CoreSlider,
  TextBox => CoreTextBox, View => CoreView, Widget => CoreWidget }
import org.nlogo.internalapi.{ AddProcedureRun,
  CompiledButton => ApiCompiledButton, CompiledModel,
  CompiledMonitor => ApiCompiledMonitor,
  CompiledSlider => ApiCompiledSlider, RunComponent }

object ModelInterfaceBuilder {
  def build(compiledModel: CompiledModel): (InterfaceArea, Map[String, Pane with RunComponent]) = {
    val model = compiledModel.model
    val speedControl  = new BasicSpeedControl()
    val interfaceArea = new InterfaceArea(speedControl)
    val interfacePane = interfaceArea.interfacePane
    val widgetsMap =
      compiledModel.compiledWidgets.flatMap {
        case compiledButton: ApiCompiledButton =>
          val button = new ButtonControl(compiledButton, compiledModel.runnableModel, speedControl.foreverInterval)
          val b = compiledButton.widget
          button.relocate(b.left, b.top)
          interfacePane.getChildren.add(button)
          Seq(compiledButton.procedureTag -> button)
        case compiledMonitor: ApiCompiledMonitor =>
          val monitor = new MonitorControl(compiledMonitor)
          val m = compiledMonitor.widget
          monitor.relocate(m.left, m.top)
          interfacePane.getChildren.add(monitor)
          Seq()
        case compiledSlider: ApiCompiledSlider =>
          val slider = new SliderControl(compiledSlider, compiledModel.runnableModel)
          val s = compiledSlider.widget
          slider.relocate(s.left, s.top)
          interfacePane.getChildren.add(slider)
          Seq()
        case other =>
          other.widget match {
            case v: CoreView    =>
              import javafx.scene.paint.Color
              val d = compiledModel.model.view.dimensions
              val c = new Canvas(model.view)
              c.relocate(v.left, v.top)
              interfacePane.getChildren.add(c)
              val gc = c.getGraphicsContext2D
              gc.setFill(Color.BLACK)
              gc.fillRect(0, 0, c.getWidth, c.getHeight)
            case _ =>
          }
        Seq()
      }
    (interfaceArea, widgetsMap.toMap)
  }
}
