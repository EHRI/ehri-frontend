package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import models.base.AccessibleEntity


object AuthoritativeSetFormat {
  import models.AuthoritativeSetF._
  import models.Entity._

  implicit val authoritativeSetWrites: Writes[AuthoritativeSetF] = new Writes[AuthoritativeSetF] {
    def writes(d: AuthoritativeSetF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val authoritativeSetReads: Reads[AuthoritativeSetF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.AuthoritativeSet)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).readNullable[String] and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(AuthoritativeSetF.apply _)

  implicit val restFormat: Format[AuthoritativeSetF] = Format(authoritativeSetReads,authoritativeSetWrites)


  private implicit val systemEventReads = SystemEventFormat.metaReads
  implicit val metaReads: Reads[AuthoritativeSetMeta] = (
    __.read[AuthoritativeSetF] and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
  )(AuthoritativeSetMeta.apply _)
}