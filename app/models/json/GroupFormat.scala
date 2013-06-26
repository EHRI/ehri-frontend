package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{AccessibleEntity, Accessor}


object GroupFormat {
  import models.GroupF._
  import models.Entity._

  implicit val groupWrites: Writes[GroupF] = new Writes[GroupF] {
    def writes(d: GroupF): JsValue = {
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

  implicit val groupReads: Reads[GroupF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Group)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(GroupF.apply _)

  implicit val restFormat: Format[GroupF] = Format(groupReads,groupWrites)
  private lazy implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[GroupMeta] = (
    __.read[GroupF] and
    (__ \ RELATIONSHIPS \ Accessor.BELONGS_REL).lazyReadNullable[List[GroupMeta]](
      Reads.list[GroupMeta]).map(_.getOrElse(List.empty[GroupMeta])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.ACCESS_REL).lazyReadNullable[List[Accessor]](
      Reads.list[Accessor]).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
  )(GroupMeta.apply _)
}