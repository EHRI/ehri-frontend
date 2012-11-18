package models.base

import models.Entity
import models.Entity
import defines.EntityType
import models.DocumentaryUnitDescriptionRepr
import models.AgentDescriptionRepr

object Description {
  def apply(e: Entity): Description = e.isA match {
    case EntityType.DocumentaryUnitDescription => DocumentaryUnitDescriptionRepr(e)
    case EntityType.AgentDescription => AgentDescriptionRepr(e)
    case _ => sys.error("Unknown description type: " + e.isA.toString())
  }
}


trait Description extends AccessibleEntity {
	val e: Entity
	
	val languageCode: String = e.property("languageCode").flatMap(_.asOpt[String]).getOrElse(sys.error("No language code found"))
}