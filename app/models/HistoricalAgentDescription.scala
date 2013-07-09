package models

import models.base._
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import play.api.libs.json.{Json, JsObject, JsValue}

case class IsaarDetail(
  datesOfExistence: Option[String] = None,
  history: Option[String] = None,
  places: Option[String] = None,
  legalStatus: Option[String] = None,
  functions: Option[String] = None,
  mandates: Option[String] = None,
  internalStructure: Option[String] = None,
  generalContext: Option[String] = None
) extends AttributeSet

case class IsaarControl(
  descriptionIdentifier: Option[String] = None,
  institutionIdentifier: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  status: Option[String] = None,
  levelOfDetail: Option[String] = None,
  datesCDR: Option[String] = None,
  languages: Option[List[String]] = None,
  scripts: Option[List[String]] = None,
  sources: Option[List[String]] = None,
  maintenanceNotes: Option[String] = None
) extends AttributeSet



object HistoricalAgentDescriptionF {

  lazy implicit val historicalAgentDescriptionFormat = json.IsaarFormat.restFormat

  implicit object Converter extends RestConvertable[HistoricalAgentDescriptionF] with ClientConvertable[HistoricalAgentDescriptionF] {
    val restFormat = models.json.IsaarFormat.restFormat

    private implicit val entityFormat = json.entityFormat
    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val datePeriodFormat = DatePeriodF.Converter.clientFormat
    private implicit val isaarDetailsFormat = Json.format[IsaarDetail]
    private implicit val isaarControlFormat = Json.format[IsaarControl]
    val clientFormat = Json.format[HistoricalAgentDescriptionF]
  }
}

case class HistoricalAgentDescriptionF(
  isA: EntityType.Value = EntityType.HistoricalAgentDescription,
  id: Option[String],
  languageCode: String,
  entityType: Isaar.HistoricalAgentType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(DatePeriodF.DATE_REL)
  dates: List[DatePeriodF] = Nil,
  details: IsaarDetail,
  control: IsaarControl,
  accessPoints: List[AccessPointF],
  unknownProperties: List[Entity] = Nil
  ) extends Model with Persistable with Description with Temporal


/*
case class HistoricalAgentDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[HistoricalAgentDescriptionF] {
  lazy val item: Option[HistoricalAgent] = e.relations(Described.REL).headOption.map(HistoricalAgent(_))

  lazy val formable: HistoricalAgentDescriptionF = {
    val json = Json.toJson(e)
    println("JSON: " + json)
    json.as[HistoricalAgentDescriptionF]
  }

  lazy val formableOpt: Option[HistoricalAgentDescriptionF] = Json.toJson(e).asOpt[HistoricalAgentDescriptionF]
}
*/
