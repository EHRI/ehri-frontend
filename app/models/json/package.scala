package models

import defines.EntityType
import play.api.libs.json._
import play.api.data.validation.ValidationError
import play.api.libs.json.JsSuccess
import play.api.libs.json.util._
import play.api.libs.functional.syntax._

/**
 * User: michaelb
 */
package object json {

  /**
   * Reader for the EntityType enum
   */
  implicit val entityTypeReads = defines.EnumUtils.enumReads(EntityType)
  implicit val entityTypeFormat = defines.EnumUtils.enumFormat(EntityType)

  implicit val entityWrites: Writes[Entity] = (
    (__ \ Entity.ID).write[String] and
      (__ \ Entity.TYPE).write[EntityType.Type](defines.EnumUtils.enumWrites) and
      (__ \ Entity.DATA).lazyWrite(Writes.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyWrite(Writes.map[List[Entity]])
    )(unlift(Entity.unapply))

  implicit val entityReads: Reads[Entity] = (
    (__ \ Entity.ID).read[String] and
      (__ \ Entity.TYPE).read[EntityType.Type](defines.EnumUtils.enumReads(EntityType)) and
      (__ \ Entity.DATA).lazyRead(Reads.map[JsValue]) and
      (__ \ Entity.RELATIONSHIPS).lazyRead(Reads.map[List[Entity]](Reads.list(entityReads)))
    )(Entity.apply _)

  implicit val entityFormat = Format(entityReads, entityWrites)


  /**
   * Reads combinator that checks if a value is equal to the expected value.
   */
  def equalsReads[T](t: T)(implicit r: Reads[T]): Reads[T] = Reads.filter(ValidationError("validate.error.incorrectType", t))(_ == t)
  def equalsFormat[T](t: T)(implicit r: Format[T]): Format[T] = Format(equalsReads(t), r)

  /**
   * Writes a list value as null if the list is empty. Reads as an empty list
   * if the path is null.
   * @param path
   * @param fmt
   * @tparam T
   * @return
   */
  def nullableListOf[T](path: JsPath)(implicit fmt: Format[T]): OFormat[List[T]] = {
    new OFormat[List[T]] {
      def reads(json: JsValue): JsResult[List[T]] = {
        json.validate[List[T]].fold(
          invalid = { err =>
            JsSuccess[List[T]](List.empty[T], path)
          },
          valid = { v =>
            JsSuccess[List[T]](v, path)
          }
        )
      }

      def writes(o: List[T]): JsObject
      = if (o.isEmpty) Json.obj()
      else path.write[List[T]].writes(o)
    }
  }
}
