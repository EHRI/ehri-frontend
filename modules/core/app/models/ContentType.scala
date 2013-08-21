package models

import models.base.AnyModel
import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._

object ContentType {
  implicit object Converter extends ClientConvertable[ContentType] with RestReadable[ContentType] {
    val restReads: Format[ContentType] = (
      (__ \ Entity.ID).format[String] and
      (__ \ Entity.TYPE).format[EntityType.Value](equalsReads(EntityType.ContentType))
    )(ContentType.apply _, unlift(ContentType.unapply _))

    val clientFormat = restReads
  }
}

case class ContentType(
  id: String,
  isA: EntityType.Value = EntityType.ContentType
) extends AnyModel
