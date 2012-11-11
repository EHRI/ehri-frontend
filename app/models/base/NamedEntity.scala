package models.base

import models.Entity

trait NamedEntity {
  this: AccessibleEntity =>
	
  val e: Entity
  val name: String = e.property("name").flatMap(_.asOpt[String]).getOrElse("")
}