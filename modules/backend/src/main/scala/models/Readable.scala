package models

import play.api.libs.json.Reads

import scala.annotation.implicitNotFound

/**
 * A type class for items that can be read from the services.
 */
@implicitNotFound("No member of type class Readable found for type ${T}")
trait Readable[T] {
  def _reads: Reads[T]
}

object Readable {
  def apply[T: Readable]: Readable[T] = implicitly[Readable[T]]
}
