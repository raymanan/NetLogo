// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.javafx

import
  org.nlogo.{ internalapi, nvm },
    internalapi.ModelUpdate,
    nvm.Procedure

// NOTE: This class isn't specific to javafx, but seemed like the most convenient place to put it.
trait ReporterMonitorable {
  def procedureTag: String
  def update(a: AnyRef): Unit
  def procedure: Procedure
}

// There is probably some way to unify this with ReporterMonitorable, but I haven't yet figured it out.
trait StaticMonitorable {
  def tags: Seq[String]
  def notifyUpdate(update: ModelUpdate): Unit
}
