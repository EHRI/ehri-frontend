package models

import backend.Entity._
import defines.EntityType
import defines.EnumUtils._
import models.base.{AnyModel, MetaModel, Model, Persistable}
import models.json._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.libs.json._
import play.api.libs.functional.syntax._

case class MaintenanceEventF(
  isA: EntityType.Value = EntityType.MaintenanceEvent,
  id: Option[String],
  date: Option[String] = None,
  source: Option[String] = None,
  agentType: Option[String] = None,
  eventType: Option[String] = None,
  order: Option[Int] = None
) extends Model with Persistable

object MaintenanceEventF {
  val DATE = "date"
  val SOURCE = "source"
  val AGENT_TYPE = "agentType"
  val EVENT_TYPE = "eventType"
  val ORDER = "order"

  import backend.Entity._

  implicit val maintenanceEventFormat: Format[MaintenanceEventF] = (
    (__ \ TYPE).formatIfEquals(EntityType.MaintenanceEvent) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ DATE).formatNullable[String] and
    (__ \ DATA \ SOURCE).formatNullable[String] and
    (__ \ DATA \ AGENT_TYPE).formatNullable[String] and
    (__ \ DATA \ EVENT_TYPE).formatNullable[String] and
    (__ \ DATA \ ORDER).formatNullable[Int]
  )(MaintenanceEventF.apply, unlift(MaintenanceEventF.unapply))

  val form = Form(mapping(
    ISA -> ignored(EntityType.MaintenanceEvent),
    ID -> optional(nonEmptyText),
    DATE -> optional(nonEmptyText),
    SOURCE -> optional(nonEmptyText),
    AGENT_TYPE -> optional(nonEmptyText),
    EVENT_TYPE -> optional(nonEmptyText),
    ORDER -> optional(number)
  )(MaintenanceEventF.apply)(MaintenanceEventF.unapply))
}

object MaintenanceEvent {

  import backend.Entity.META

  implicit val metaReads: Reads[MaintenanceEvent] = (
    __.read[MaintenanceEventF] and
    (__ \ META).readWithDefault(Json.obj())
  )(MaintenanceEvent.apply _)

  implicit object Converter extends backend.Readable[MaintenanceEvent] {
    val restReads = metaReads
  }
}

case class MaintenanceEvent(
  model: MaintenanceEventF,
  meta: JsObject = JsObject(Seq())
) extends AnyModel with MetaModel[MaintenanceEventF] {
  override def toStringLang(implicit messages: Messages) = s"Maintenance Event: (${model.source})"
}
