package models

import models.base._
import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import defines.{ContentTypes, EntityType, EventType}
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.Messages
import play.api.libs.json.JsObject
import backend.Entity

object SystemEventF {

  final val TIMESTAMP = "timestamp"
  final val LOG_MESSAGE = "logMessage"
  final val EVENT_TYPE = "eventType"
  final val FORMAT = "yyyy-MM-dd'T'HH:mm:ssSSSZ"

  import SystemEventF.{EVENT_TYPE => EVENT_PROP, _}
  import Entity._

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
    (__ \ TYPE).readIfEquals(EntityType.SystemEvent) and
    (__ \ ID).readNullable[String] and
    // NB: Default Joda DateTime format is less flexible then
    // using the constructor from a string
    (__ \ DATA \ TIMESTAMP).read[String].map(new DateTime(_)) and
    (__ \ DATA \ LOG_MESSAGE).readNullable[String] and
    (__ \ DATA \ EVENT_PROP).readNullable[EventType.Value]
  )(SystemEventF.apply _)

  implicit object Converter extends backend.Readable[SystemEventF] {
    val restReads = Format(systemEventReads,systemEventWrites)
  }
}

case class SystemEventF(
  isA: EntityType.Value = EntityType.SystemEvent,
  id: Option[String],
  timestamp: DateTime,
  logMessage: Option[String] = None,
  eventType: Option[EventType.Value] = None
) extends Model {
  lazy val datetime = ISODateTimeFormat.dateTime.withZoneUTC.print(timestamp)
}

object SystemEvent {
  import play.api.libs.functional.syntax._
  import SystemEventF.{EVENT_TYPE => EVENT_PROP, _}
  import Entity._
  import eu.ehri.project.definitions.Ontology._

  implicit val metaReads: Reads[SystemEvent] = (
    __.read[SystemEventF] and
    (__ \ RELATIONSHIPS \ EVENT_HAS_SCOPE).lazyNullableHeadReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ EVENT_HAS_FIRST_SUBJECT).lazyNullableHeadReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ EVENT_HAS_ACTIONER).lazyNullableHeadReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ VERSION_HAS_EVENT).nullableHeadReads(Version.Converter.restReads) and
    (__ \ META).readWithDefault(Json.obj())
  )(SystemEvent.apply _)

  implicit object SystemEventResource extends backend.ContentType[SystemEvent]  {
    val entityType = EntityType.SystemEvent
    val contentType = ContentTypes.SystemEvent
    val restReads = metaReads
  }
}

case class SystemEvent(
  model: SystemEventF,
  scope: Option[AnyModel] = None,
  firstSubject: Option[AnyModel] = None,
  actioner: Option[Accessor] = None,
  version: Option[Version] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[SystemEventF]
  with Holder[AnyModel] {

  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(model.timestamp)

  /**
   * If the event is of a certain type (link, annotate) the effective
   * subject is the scope in which the link or annotation is made,
   * rather than the link/annotation itself.
   */
  def effectiveSubject: Option[AnyModel] =
    if (model.eventType == Some(EventType.link) || model.eventType == Some(EventType.annotation))
      scope else firstSubject

  import EventType._

  /**
   * For display purposes, collapse certain specific event types to a more
   * general "effective" type: create, modify,delete, etc.
   */
  def effectiveType: Option[EventType.Value] = model.eventType.map {
    case `createDependent`|`modifyDependent`|`deleteDependent` => modification
    case `setGlobalPermissions`|`setItemPermissions`|`setVisibility`|`addGroup`|`removeGroup` => modification
    case et => et
  }

  override def toStringLang(implicit messages: play.api.i18n.Messages) =
    Messages(isA + "." + model.eventType.map(_.toString).getOrElse("unknown"))(messages)
}

