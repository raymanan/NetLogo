// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.internalapi

sealed trait ModelAction

case class AddProcedureRun(widgetTag: String, isForever: Boolean, interval: Long) extends ModelAction

// this may need to contain data on a suspended run, a procedure and forever flag may be insufficient
case class StopProcedure(jobTag: String) extends ModelAction
