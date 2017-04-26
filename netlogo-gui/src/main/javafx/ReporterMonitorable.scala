// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.nvm.Procedure

// NOTE: This class isn't specific to javafx, but seemed like the most convenient place to put it.
trait ReporterMonitorable {
  def procedureTag: String
  def update(a: AnyRef): Unit
  def procedure: Procedure
}
