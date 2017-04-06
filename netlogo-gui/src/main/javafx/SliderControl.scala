// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo(UTF8)

package org.nlogo.javafx

import java.lang.{ Boolean => JBoolean, Double => JDouble }
import java.util.concurrent.atomic.AtomicReference

import javafx.beans.value.ObservableValue
import javafx.beans.property.DoubleProperty
import javafx.event.{ Event, EventHandler }
import javafx.fxml.{ FXML, FXMLLoader }
import javafx.geometry.Orientation
import javafx.scene.control.{ Label, Slider }
import javafx.scene.layout.GridPane
import javafx.scene.input.TouchEvent

import com.sun.javafx.scene.control.skin.SliderSkin

import org.nlogo.internalapi.{ CompiledSlider => ApiCompiledSlider, Monitorable,
  RunnableModel, UpdateInterfaceGlobal }

import org.nlogo.core.{ Slider => CoreSlider }

// TODO: This only allows for sliders with constant mins and maxes
class SliderControl(compiledSlider: ApiCompiledSlider, runnableModel: RunnableModel)
  extends GridPane {

  @FXML
  var slider: Slider = _

  @FXML
  var nameLabel: Label = _

  @FXML
  var valueLabel: Label = _

  val model = compiledSlider.widget

  val currentValue = new AtomicReference[JDouble](Double.box(model.default))

  locally {
    val loader = new FXMLLoader(getClass.getClassLoader.getResource("Slider.fxml"))
    loader.setController(this)
    loader.setRoot(this)
    loader.load()
    setPrefSize(model.right - model.left, model.bottom - model.top)
    nameLabel.setText(model.display orElse model.variable getOrElse "")
    slider.setSnapToTicks(true)
    slider.setMinorTickCount(0)
    slider.setShowTickMarks(true)
    slider.valueProperty.addListener(new javafx.beans.value.ChangeListener[Number]() {
      def changed(o: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = {
        if (! slider.valueChangingProperty.get()) {
          currentValue.set(Double.box(newValue.doubleValue))
          model.variable.foreach { variableName =>
            runnableModel.submitAction(UpdateInterfaceGlobal(variableName.toUpperCase, currentValue))
          }
        }
      }
    })
    bindSliderToLabel(slider, valueLabel)
    bindPropertyToMonitorable(slider.minProperty,           compiledSlider.min)
    bindPropertyToMonitorable(slider.maxProperty,           compiledSlider.max)
    bindPropertyToMonitorable(slider.valueProperty,         compiledSlider.value)
    bindPropertyToMonitorable(slider.majorTickUnitProperty, compiledSlider.inc)
  }

  protected def bindPropertyToMonitorable(d: DoubleProperty, m: Monitorable[Double]): Unit = {
    d.setValue(m.defaultValue)
    m.onUpdate({ updated => d.setValue(updated) })
  }

  protected def bindSliderToLabel(s: Slider, l: Label): Unit = {
    def adjustSlider(te: TouchEvent): Unit = {
      s.getSkin match {
        case ss: SliderSkin =>
          if (ss.getSkinnable.getOrientation == Orientation.HORIZONTAL)
            s.adjustValue((te.getTouchPoint.getX / s.getWidth) * (s.getMax - s.getMin) + s.getMin)
          else
            s.adjustValue((te.getTouchPoint.getY / s.getHeight) * (s.getMax - s.getMin) + s.getMin)
        case _ =>
      }
    }
    s.setOnTouchMoved(handler(adjustSlider _))
    s.setOnTouchPressed(handler(adjustSlider _))
    s.valueProperty.addListener(new javafx.beans.value.ChangeListener[Number]() {
      def changed(observable: ObservableValue[_ <: Number], oldValue: Number, newValue: Number): Unit = {
        l.textProperty.setValue(newValue.toString.take(5) + model.units.map(" " + _).getOrElse(""))
      }
    })
    l.setText(s.getValue.toString.take(5) + model.units.map(" " + _).getOrElse(""))
  }

  protected def handler[T <: Event](f: T => Unit): EventHandler[T] = {
    new EventHandler[T]() {
      override def handle(event: T): Unit = {
        f(event)
      }
    }
  }
}
