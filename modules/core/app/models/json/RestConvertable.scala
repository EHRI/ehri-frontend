package models.json

import play.api.libs.json.{Reads, Writes, Format}
import defines.EntityType

/**
 * Type classes for connecting models to REST functionality.
 */

trait RestResource[T] {
  /**
   * The type of entity that informs the REST URL Path.
   */
  def entityType: EntityType.Value

  /**
   * Default serialization params for specific types.
   */
  def defaultParams: Seq[(String, String)] = Seq.empty
}

trait RestReadable[T] {
  val restReads: Reads[T]
}

trait RestConvertable[T] {
  val restFormat: Format[T]
}

trait ClientConvertable[T] {
  val clientFormat: Writes[T]
}
