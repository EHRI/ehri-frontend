package models

import models.base.AnyModel
import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import backend.{Entity, Readable}


object ContentType {
  val reads: Reads[ContentType] = (
    (__ \ Entity.ID).read[String] and
    (__ \ Entity.TYPE).readIfEquals(EntityType.ContentType)
  )(ContentType.apply _)

  val writes: Writes[ContentType] = (
    (__ \ Entity.ID).write[String] and
    (__ \ Entity.TYPE).write[EntityType.Value]
  )(unlift(ContentType.unapply))

  implicit val restFormat = Format(reads, writes)

  implicit object Converter extends Readable[ContentType] {
    val restReads = reads
  }
}

case class ContentType(
  id: String,
  isA: EntityType.Value = EntityType.ContentType
) extends AnyModel
