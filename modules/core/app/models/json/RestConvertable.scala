package models.json

import play.api.libs.json.{Reads, Writes, Format}
import defines.EntityType

/**
 * Type classes for connecting models to REST functionality.
 */

trait RestResource[T] {
  val entityType: EntityType.Value
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
