package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.{EntityType,EventType}
import defines.EnumUtils._
import org.joda.time.DateTime
import models.base.{Accessor, AnyModel}


object SystemEventFormat {
  import SystemEventF.{EVENT_TYPE => EVENT_PROP, _}
  import Entity._
  import eu.ehri.project.definitions.Ontology._

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
          EVENT_PROP -> d.eventType
        )
      )
    }
  }

  implicit val systemEventReads: Reads[SystemEventF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.SystemEvent)) and
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ TIMESTAMP).read[String].map(new DateTime(_)) and
      (__ \ DATA \ LOG_MESSAGE).readNullable[String] and
      (__ \ DATA \ EVENT_PROP).readNullable[EventType.Value]
    )(SystemEventF.apply _)

  implicit val restFormat: Format[SystemEventF] = Format(systemEventReads,systemEventWrites)

  implicit val metaReads: Reads[SystemEvent] = (
    __.read[SystemEventF] and
    (__ \ RELATIONSHIPS \ EVENT_HAS_SCOPE).lazyReadNullable[List[AnyModel]](
      Reads.list(AnyModel.Converter.restReads)).map(_.flatMap(_.headOption)) and
      (__ \ RELATIONSHIPS \ EVENT_HAS_FIRST_SUBJECT).lazyReadNullable[List[AnyModel]](
        Reads.list(AnyModel.Converter.restReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ EVENT_HAS_ACTIONER).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.flatMap(_.headOption)) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(SystemEvent.apply _)
}
