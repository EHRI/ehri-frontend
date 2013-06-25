package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.{EntityType,EventType}
import defines.EnumUtils._
import org.joda.time.DateTime
import models.base.AccessibleEntity


object SystemEventFormat {
  import SystemEventF._
  import Entity._

  implicit val eventTypeReads = defines.EnumUtils.enumFormat(defines.EventType)

  // We won't need to use this, since events are created automatically
  // but doing it anyway just for completeness
  implicit val systemEventWrites = new Writes[SystemEventF] {
    def writes(d: SystemEventF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TIMESTAMP -> d.timestamp,
          LOG_MESSAGE -> d.logMessage,
          EVENT_TYPE -> d.eventType
        )
      )
    }
  }

  implicit val systemEventReads: Reads[SystemEventF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.SystemEvent)) and
      (__ \ ID).read[String] and
      (__ \ DATA \ TIMESTAMP).read[String].map(new DateTime(_)) and
      (__ \ DATA \ LOG_MESSAGE).readNullable[String] and
      (__ \ DATA \ EVENT_TYPE).readNullable[EventType.Value]
    )(SystemEventF.apply _)

  implicit val restFormat: Format[SystemEventF] = Format(systemEventReads,systemEventWrites)

  private implicit val groupReads = GroupFormat.metaReads
  private implicit val userReads = UserProfileFormat.metaReads

  implicit val metaReads: Reads[SystemEventMeta] = (
    __.read[SystemEventF] and
    (__ \ RELATIONSHIPS \ SystemEvent.ACTIONER_REL).lazyReadNullable[List[UserProfileMeta]](
      Reads.list[UserProfileMeta]).map(_.flatMap(_.headOption))
  )(SystemEventMeta.apply _)
}
