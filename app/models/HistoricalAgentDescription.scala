package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType

object HistoricalAgentDescriptionF {

  case class Details(
    datesOfExistence: Option[String] = None,
    history: Option[String] = None,
    places: Option[String] = None,
    legalStatus: Option[String] = None,
    functions: Option[String] = None,
    mandates: Option[String] = None,
    internalStructure: Option[String] = None,
    generalContext: Option[String] = None
    ) extends AttributeSet

  case class Control(
    descriptionIdentifier: Option[String] = None,
    institutionIdentifier: Option[String] = None,
    rulesAndConventions: Option[String] = None,
    status: Option[String] = None,
    levelOfDetail: Option[String] = None,
    datesCDR: Option[String] = None,
    languages: Option[List[String]] = None,
    scripts: Option[List[String]] = None,
    sources: Option[String] = None,
    maintenanceNotes: Option[String] = None
    ) extends AttributeSet

  lazy implicit val historicalAgentDescriptionFormat = json.IsaarFormat.isaarFormat
}

case class HistoricalAgentDescriptionF(
  id: Option[String],
  languageCode: String,
  entityType: Isaar.HistoricalAgentType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  details: HistoricalAgentDescriptionF.Details,
  control: HistoricalAgentDescriptionF.Control,
  accessPoints: List[AccessPointF]
  ) extends Persistable {
  val isA = EntityType.HistoricalAgentDescription

  def toJson: JsValue = Json.toJson(this)
}

case class HistoricalAgentDescription(val e: Entity) extends Description with Formable[HistoricalAgentDescriptionF] {
  lazy val item: Option[HistoricalAgent] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(HistoricalAgent(_))

  lazy val formable: HistoricalAgentDescriptionF = {
    val json = Json.toJson(e)
    println("JSON: " + json)
    json.as[HistoricalAgentDescriptionF]
  }

  lazy val formableOpt: Option[HistoricalAgentDescriptionF] = Json.toJson(e).asOpt[HistoricalAgentDescriptionF]
}
