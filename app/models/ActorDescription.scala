package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType

case class ActorDescription(val e: Entity) extends Description with Formable[ActorDescriptionF] {
  lazy val item: Option[Actor] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(Actor(_))

  import json.IsaarFormat._
  lazy val formable: ActorDescriptionF = {
    val json = Json.toJson(e)
    println("JSON: " + json)
    json.as[ActorDescriptionF]
  }

  lazy val formableOpt: Option[ActorDescriptionF] = Json.toJson(e).asOpt[ActorDescriptionF]
}

object ActorDescriptionF {

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

}

case class ActorDescriptionF(
  id: Option[String],
  languageCode: String,
  entityType: Isaar.ActorType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  details: ActorDescriptionF.Details,
  control: ActorDescriptionF.Control
  ) extends Persistable {
  val isA = EntityType.ActorDescription

  import json.IsaarFormat._
  def toJson: JsValue = Json.toJson(this)
}


