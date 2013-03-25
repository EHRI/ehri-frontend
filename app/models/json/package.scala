package models

import defines.EntityType
import play.api.libs.json.Reads
import play.api.data.validation.ValidationError

/**
 * User: michaelb
 */
package object json {

  /**
   * Reader for the EntityType enum
   */
  implicit val entityTypeReads = defines.EnumUtils.enumReads(EntityType)

  /**
   * Reads combinator that checks if a value is equal to the expected value.
   */
  def equalsReads[T](t: T)(implicit r: Reads[T]): Reads[T] = Reads.filter(ValidationError("validate.error.incorrectType", t))(_ == t)

}
