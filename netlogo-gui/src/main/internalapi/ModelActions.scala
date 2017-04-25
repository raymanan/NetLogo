// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.internalapi

import java.util.concurrent.atomic.AtomicReference
import org.nlogo.core.AgentKind

sealed trait ModelAction

case class UpdateInterfaceGlobal(name: String, value: AtomicReference[_ <: AnyRef]) extends ModelAction

case class UpdateVariable(name: String, agentKind: AgentKind, who: Int, expectedValue: AnyRef, updateValue: AnyRef)
  extends ModelAction

case class AddProcedureRun(widgetTag: String, isForever: Boolean, interval: Long) extends ModelAction

// this may need to contain data on a suspended run, a procedure and forever flag may be insufficient
case class StopProcedure(jobTag: String) extends ModelAction
