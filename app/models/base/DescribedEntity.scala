package models.base

import models._
import defines.EntityType
import models.Group
import models.DocumentaryUnit
import models.Repository

object DescribedEntity {
  final val DESCRIBES_REL = "describes"

  final val DESCRIPTIONS = "descriptions"

  def apply(e: Entity): DescribedEntity = e.isA match {
    case EntityType.DocumentaryUnit => DocumentaryUnit(e)
    case EntityType.Repository => Repository(e)
    case EntityType.Concept => Concept(e)

    case _ => sys.error("Unknown entity type for DescribedEntity: " + e.isA.toString())
  }
}

trait DescribedEntity extends AccessibleEntity {

	def descriptions: List[Description]
}