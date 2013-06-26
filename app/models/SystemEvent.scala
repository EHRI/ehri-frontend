package models

import models.base.{Model, MetaModel, AccessibleEntity, Accessor}
import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import play.api.i18n.Messages
import defines.{EntityType, EventType}
import play.api.libs.json.JsObject
import models.json.{RestReadable, ClientConvertable}

object SystemEventF {
  final val TIMESTAMP = "timestamp"
  final val LOG_MESSAGE = "logMessage"
  final val EVENT_TYPE = "eventType"
  final val FORMAT = "yyyy-MM-dd'T'HH:mm:ssSSSZ"
}

case class SystemEventF(
  isA: EntityType.Value = EntityType.SystemEvent,
  id: Option[String],
  timestamp: DateTime,
  logMessage: Option[String] = None,
  eventType: Option[EventType.Value] = None
) extends Model

object SystemEvent {
  val ACTIONER_REL = "hasActioner"
  val SCOPE_REL = "hasEventScope"
}

case class SystemEvent(val e: Entity) extends AccessibleEntity {

  import SystemEvent._

  val timeStamp: DateTime = e.property(SystemEventF.TIMESTAMP).flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))
  val logMessage: Option[String] = e.stringProperty(SystemEventF.LOG_MESSAGE)
  val actioner: Option[Accessor] = e.relations(ACTIONER_REL).headOption.map(Accessor(_))
  val scope: Option[ItemWithId] = e.relations(SCOPE_REL).headOption.map(ItemWithId(_))

  implicit val eventTypeReads = defines.EnumUtils.enumReads(EventType)
  val eventType: Option[EventType.Value] = e.property(SystemEventF.EVENT_TYPE).flatMap(_.asOpt[EventType.Value])

  /**
   * Standard time output.
   * @return
   */
  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(timeStamp)

  /**
   * ISO date time output.
   * @return
   */
  //def dateTime = ISODateTimeFormat.dateTime().print(timeStamp)
  // NB: Because Solr barfs at the time zone +01
  def dateTime = ISODateTimeFormat.dateTime.withZoneUTC.print(timeStamp)

  override def toString = {
    val t = eventType.map(_.toString).getOrElse("unknown")
    Messages("systemEvents." + t)
  }
}

object SystemEventMeta {
  implicit object Converter extends ClientConvertable[SystemEventMeta] with RestReadable[SystemEventMeta] {
    val restReads = models.json.SystemEventFormat.metaReads
    val clientFormat = models.json.client.systemEventMetaFormat
  }
}

case class SystemEventMeta(
  model: SystemEventF,
  actioner: Option[UserProfileMeta] = None
) extends MetaModel[SystemEventF] {
  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(model.timestamp)
}

