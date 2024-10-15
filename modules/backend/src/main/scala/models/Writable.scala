package models

import play.api.libs.json.Format

import scala.annotation.implicitNotFound

/**
 * A type class for items that can be saved (written)
 * to the services.
 */
@implicitNotFound("No member of type class Writable found for type ${T}")
trait Writable[T] {
  def _format: Format[T]
}

object Writable {
  def apply[T: Writable]: Writable[T] = implicitly[Writable[T]]
}
