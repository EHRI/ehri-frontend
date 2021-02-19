package services.data

import models.EntityType

import scala.annotation.implicitNotFound

/**
 * Type classes for connecting models to REST functionality.
 */
@implicitNotFound("No member of type class Resource found for type ${T}")
trait Resource[T] extends Readable[T] {
  /**
   * The type of entity that informs the REST URL Path.
   */
  def entityType: EntityType.Value

  /**
   * Default serialization params for specific types.
   */
  def defaultParams: Seq[(String, String)] = Seq.empty
}

object Resource {
  def apply[T: Resource]: Resource[T] = implicitly[Resource[T]]
}
