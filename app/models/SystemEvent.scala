package models

import models.base.{AnyModel, Model, MetaModel}
import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import defines.{EntityType, EventType}
import models.json.{RestReadable, ClientConvertable}
import play.api.libs.json.{Format, Reads}

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
) extends Model {
  lazy val datetime = ISODateTimeFormat.dateTime.withZoneUTC.print(timestamp)
}

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
  scope: Option[AnyModel] = None,
  actioner: Option[UserProfileMeta] = None
) extends AnyModel
  with MetaModel[SystemEventF] {
  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(model.timestamp)
}

