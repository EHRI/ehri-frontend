package backend

import defines.EntityType

/**
 * Type classes for connecting models to REST functionality.
 */

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
