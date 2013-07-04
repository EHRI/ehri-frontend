package models

import models.base.AccessibleEntity
import models.base.Accessor
import org.joda.time.DateTime
import org.joda.time.format.{ISODateTimeFormat, DateTimeFormat}
import play.api.i18n.Messages
import defines.EventType

object SystemEvent {
  val ACTIONER_REL = "hasActioner"
  val SCOPE_REL = "hasEventScope"

  final val TIMESTAMP = "timestamp"
  final val LOG_MESSAGE = "logMessage"
  final val EVENT_TYPE = "eventType"
}

case class SystemEvent(val e: Entity) extends AccessibleEntity {

  import SystemEvent._

  val timeStamp: DateTime = e.property(TIMESTAMP).flatMap(_.asOpt[String]).map(new DateTime(_))
    .getOrElse(sys.error("No timestamp found on action [%s]".format(e.id)))
  val logMessage: Option[String] = e.stringProperty(LOG_MESSAGE)
  val actioner: Option[Accessor] = e.relations(ACTIONER_REL).headOption.map(Accessor(_))
  val scope: Option[ItemWithId] = e.relations(SCOPE_REL).headOption.map(ItemWithId(_))

  implicit val eventTypeReads = defines.EnumUtils.enumReads(EventType)
  val eventType: Option[EventType.Value] = e.property(EVENT_TYPE).flatMap(_.asOpt[EventType.Value])

  /**
   * Standard time output.
   * @return
   */
  def time = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss").print(timeStamp)

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

