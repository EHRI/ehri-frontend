package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.{EntityType,EventType}
import defines.EnumUtils._
import org.joda.time.DateTime
import models.base.AnyModel


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
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ TIMESTAMP).read[String].map(new DateTime(_)) and
      (__ \ DATA \ LOG_MESSAGE).readNullable[String] and
      (__ \ DATA \ EVENT_TYPE).readNullable[EventType.Value]
    )(SystemEventF.apply _)

  implicit val restFormat: Format[SystemEventF] = Format(systemEventReads,systemEventWrites)

  implicit val metaReads: Reads[SystemEvent] = (
    __.read[SystemEventF] and
    (__ \ RELATIONSHIPS \ SystemEventF.SCOPE_REL).lazyReadNullable[List[AnyModel]](
      Reads.list(AnyModel.Converter.restReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ SystemEventF.ACTIONER_REL).lazyReadNullable[List[UserProfile]](
      Reads.list(UserProfileFormat.metaReads)).map(_.flatMap(_.headOption))
  )(SystemEvent.apply _)
}
