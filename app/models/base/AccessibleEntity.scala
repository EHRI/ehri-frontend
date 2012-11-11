package models.base

import models.Entity


trait AccessibleEntity {
  
  val e: Entity
  
  val ACCESS_REL = "access"
  
  def identifier = e.property("identifier").flatMap(_.asOpt[String]).getOrElse(sys.error("No 'identifier' property found."))
		  	
  def accessors = e.relations(ACCESS_REL).map(Accessor(_))

}