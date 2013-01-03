package models.base

import models.Entity

object AccessibleEntity {
  val IDENTIFIER = Field("identifier", "Identifier")
}

trait AccessibleEntity extends WrappedEntity {
  
  val ACCESS_REL = "access"
  
  def identifier = e.property("identifier").flatMap(_.asOpt[String]).getOrElse(sys.error("No 'identifier' property found."))
  def accessors = e.relations(ACCESS_REL).map(Accessor(_))

}