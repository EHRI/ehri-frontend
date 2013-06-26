package models

import models.base.{Model, MetaModel, Accessor}
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

object SystemEventMeta {
  implicit object Converter extends ClientConvertable[SystemEventMeta] with RestReadable[SystemEventMeta] {
    val restReads = models.json.SystemEventFormat.metaReads
    val clientFormat = models.json.client.systemEventMetaFormat
  }
}

case class SystemEventMeta(
  model: SystemEventF,
  scope: Option[MetaModel[_]] = None,
  actioner: Option[UserProfileMeta] = None
) extends MetaModel[SystemEventF] {
  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(model.timestamp)
}

