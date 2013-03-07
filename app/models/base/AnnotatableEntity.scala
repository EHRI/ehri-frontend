package models.base

import models._
import scala.Some
import scala.Some
import scala.Some
import models.Group
import models.Repository
import models.SystemEvent
import models.DocumentaryUnit
import scala.Some

object AnnotatableEntity {
  def fromEntity(e: Entity): Option[AnnotatableEntity] = {
    import defines.EntityType

    /**
     * Attempt to instantiate the model representation of this entity.
     * @return
     */
    e.`type` match {
      case EntityType.Concept => Some(Concept(e))
      case EntityType.Repository => Some(Repository(e))
      case EntityType.DocumentaryUnit => Some(DocumentaryUnit(e))
      case EntityType.Actor => Some(Actor(e))
      case EntityType.Annotation => Some(Annotation(e))
      case _ => None
    }
  }
}

/**
 * Trait for entities that can be annotated.
 */
trait AnnotatableEntity extends AccessibleEntity