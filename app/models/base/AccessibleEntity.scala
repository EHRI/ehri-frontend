package models.base

import models.SystemEvent

object AccessibleEntity {
  val IDENTIFIER = "identifier"
  val NAME = "name"

  final val EVENT_REL = "lifecycleEvent"
  final val ACCESS_REL = "access"
}

trait AccessibleEntity extends WrappedEntity {
  import AccessibleEntity._

  val nameProperty = NAME
  
  def identifier = e.property(IDENTIFIER).flatMap(_.asOpt[String]).getOrElse(sys.error("No 'identifier' property found."))
  def accessors = e.relations(ACCESS_REL).map(Accessor(_))
  def latestEvent: Option[SystemEvent] = e.relations(EVENT_REL).headOption.map(SystemEvent(_))

  override def toString = e.stringProperty(nameProperty).getOrElse(identifier)

}