package models.base

import models.Entity
import defines.EntityType
import models.DocumentaryUnitDescription
import models.RepositoryDescription

object Description {
  def apply(e: Entity): Description = e.isA match {
    case EntityType.DocumentaryUnitDescription => DocumentaryUnitDescription(e)
    case EntityType.RepositoryDescription => RepositoryDescription(e)
    case _ => sys.error("Unknown description type: " + e.isA.toString())
  }
}


trait Description extends WrappedEntity {
	val e: Entity

  val item: Option[AccessibleEntity]
  val name = e.stringProperty(AccessibleEntity.NAME).getOrElse(id)
	val languageCode: String = e.stringProperty("languageCode").getOrElse("Unknown language")

  override def toString = name
}