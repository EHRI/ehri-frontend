package models

import models.base.{AnyModel, Model, MetaModel}
import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import defines.{EntityType, EventType}
import models.json.{RestReadable, ClientConvertable}
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.i18n.Messages

object SystemEventF {
  val ACTIONER_REL = "hasActioner"
  val SCOPE_REL = "hasEventScope"

  final val TIMESTAMP = "timestamp"
  final val LOG_MESSAGE = "logMessage"
  final val EVENT_TYPE = "eventType"
  final val FORMAT = "yyyy-MM-dd'T'HH:mm:ssSSSZ"

  implicit object Converter extends RestReadable[SystemEventF] with ClientConvertable[SystemEventF] {
    val restReads = models.json.SystemEventFormat.restFormat
    val clientFormat = Json.format[SystemEventF]
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

  implicit object Converter extends ClientConvertable[SystemEvent] with RestReadable[SystemEvent] {
    private implicit val systemEventFormat = Json.format[SystemEventF]

    val restReads = models.json.SystemEventFormat.metaReads
    implicit val clientFormat: Format[SystemEvent] = (
      __.format[SystemEventF](SystemEventF.Converter.clientFormat) and
        (__ \ "scope").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
        (__ \ "user").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat)
      )(SystemEvent.apply _, unlift(SystemEvent.unapply _))


  }

}

case class SystemEvent(
  model: SystemEventF,
  scope: Option[AnyModel] = None,
  actioner: Option[UserProfile] = None
) extends AnyModel
  with MetaModel[SystemEventF] {

  def time = DateTimeFormat.forPattern(SystemEventF.FORMAT).print(model.timestamp)

  def toStringLang = Messages("systemEvents." + model.eventType.map(_.toString).getOrElse("unknown"))
}

