package models

import models.base.AnyModel
import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import services.data.Readable


/**
  * A data type limiting the scope of a permission grant.
  * Instances correspond to e.g. "DocumentaryUnit", "Repository" etc
  */
case class DataContentType(
  id: String,
  isA: EntityType.Value = EntityType.ContentType
) extends AnyModel


object DataContentType {
  val format: Format[DataContentType] = (
    (__ \ Entity.ID).format[String] and
    (__ \ Entity.TYPE).formatIfEquals(EntityType.ContentType)
  )(DataContentType.apply, unlift(DataContentType.unapply))

  implicit object Converter extends Readable[DataContentType] {
    val restReads: Format[DataContentType] = format
  }
}