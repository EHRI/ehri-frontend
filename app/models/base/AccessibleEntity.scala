package models.base

import models.Entity

object AccessibleEntity {
  val IDENTIFIER = "identifier"
  val NAME = "name"
}

trait AccessibleEntity extends WrappedEntity {
  import AccessibleEntity._

  val ACCESS_REL = "access"
  
  def identifier = e.property(IDENTIFIER).flatMap(_.asOpt[String]).getOrElse(sys.error("No 'identifier' property found."))
  def accessors = e.relations(ACCESS_REL).map(Accessor(_))

  override def toString = e.stringProperty(NAME).getOrElse(identifier)

}