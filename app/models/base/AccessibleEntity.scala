package models.base

import models.Entity


trait AccessibleEntity {
  
  val e: Entity
  
  val ACCESS_REL = "access"
  
  def identifier = e.property("identifier").flatMap(_.asOpt[String]).getOrElse(sys.error("No 'identifier' property found."))
  
  // Proxy methods - TODO: Reduce the need for these?
  def stringProperty(name: String) = e.stringProperty(name)
  def isA = e.isA
  def property(name: String) = e.property(name)
  def data = e.data
		  	
  def accessors = e.relations(ACCESS_REL).map(Accessor(_))

}