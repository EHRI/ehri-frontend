package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType


object DocumentaryUnitDescriptionF {

  case class Context(
    adminBiogHistory: Option[String] = None,
    archivalHistory: Option[String] = None,
    acquisition: Option[String] = None
  ) extends AttributeSet

  case class Content(
    scopeAndContent: Option[String] = None,
    appraisal: Option[String] = None,
    accruals: Option[String] = None,
    systemOfArrangement: Option[String] = None
  ) extends AttributeSet

  case class Conditions(
    conditionsOfAccess: Option[String] = None,
    conditionsOfReproduction: Option[String] = None,
    languageOfMaterials: Option[List[String]] = None,
    scriptOfMaterials: Option[List[String]] = None,
    physicalCharacteristics: Option[String] = None,
    findingAids: Option[String] = None
  ) extends AttributeSet

  case class Materials(
    locationOfOriginals: Option[String] = None,
    locationOfCopies: Option[String] = None,
    relatedUnitsOfDescription: Option[String] = None,
    publicationNote: Option[String] = None
  ) extends AttributeSet


  case class Control(
    archivistNote: Option[String] = None,
    rulesAndConventions: Option[String] = None,
    datesOfDescriptions: Option[String] = None
    )

  lazy implicit val documentaryUnitDescriptionFormat = json.IsadGFormat.isadGFormat
}

case class DocumentaryUnitDescriptionF(
  id: Option[String],
  languageCode: String,
  name: String,
  `abstract`: Option[String],
  @Annotations.Relation(TemporalEntity.DATE_REL)
  dates: List[DatePeriodF] = Nil,
  levelOfDescription: Option[IsadG.LevelOfDescription.Value] = None,
  extentAndMedium: Option[String] = None,
  context: DocumentaryUnitDescriptionF.Context,
  content: DocumentaryUnitDescriptionF.Content,
  conditions: DocumentaryUnitDescriptionF.Conditions,
  materials: DocumentaryUnitDescriptionF.Materials,
  notes: Option[List[String]] = None,
  control: DocumentaryUnitDescriptionF.Control,
  accessPoints: List[AccessPointF]
  ) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription

  def toJson: JsValue = Json.toJson(this)
}

case class DocumentaryUnitDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[DocumentaryUnitDescriptionF] {

  import IsadG._

  // This should have one logical object
  val item: Option[DocumentaryUnit] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(DocumentaryUnit(_))

  // What to display on search listings
  lazy val displayText: Option[String] = e.stringProperty(ABSTRACT) orElse e.stringProperty(SCOPE_CONTENT)

  lazy val formable = Json.toJson(e).as[DocumentaryUnitDescriptionF]
  lazy val formableOpt = Json.toJson(e).asOpt[DocumentaryUnitDescriptionF]
}