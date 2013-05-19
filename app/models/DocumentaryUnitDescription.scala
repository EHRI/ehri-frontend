package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType


object DocumentaryUnitDescriptionF {

  case class Context(
    val adminBiogHistory: Option[String] = None,
    val archivalHistory: Option[String] = None,
    val acquisition: Option[String] = None
    ) extends AttributeSet

  case class Content(
    val scopeAndContent: Option[String] = None,
    val appraisal: Option[String] = None,
    val accruals: Option[String] = None,
    val systemOfArrangement: Option[String] = None
    ) extends AttributeSet

  case class Conditions(
    val conditionsOfAccess: Option[String] = None,
    val conditionsOfReproduction: Option[String] = None,
    val languageOfMaterials: Option[List[String]] = None,
    val scriptOfMaterials: Option[List[String]] = None,
    val physicalCharacteristics: Option[String] = None,
    val findingAids: Option[String] = None
    ) extends AttributeSet

  case class Materials(
    val locationOfOriginals: Option[String] = None,
    val locationOfCopies: Option[String] = None,
    val relatedUnitsOfDescription: Option[String] = None,
    val publicationNote: Option[String] = None
    ) extends AttributeSet

  case class Control(
    val archivistNote: Option[String] = None,
    val rulesAndConventions: Option[String] = None,
    val datesOfDescriptions: Option[String] = None
    )

  lazy implicit val documentaryUnitDescriptionFormat = json.IsadGFormat.isadGFormat
}

case class DocumentaryUnitDescriptionF(
  val id: Option[String],
  val languageCode: String,
  val title: Option[String] = None,
  @Annotations.Relation(TemporalEntity.DATE_REL)
  val dates: List[DatePeriodF] = Nil,
  val levelOfDescription: Option[IsadG.LevelOfDescription.Value] = None,
  val extentAndMedium: Option[String] = None,
  val context: DocumentaryUnitDescriptionF.Context,
  val content: DocumentaryUnitDescriptionF.Content,
  val conditions: DocumentaryUnitDescriptionF.Conditions,
  val materials: DocumentaryUnitDescriptionF.Materials,
  val control: DocumentaryUnitDescriptionF.Control,
  val accessPoints: List[AccessPointF]
  ) extends Persistable {
  val isA = EntityType.DocumentaryUnitDescription

  def toJson: JsValue = Json.toJson(this)
}

case class DocumentaryUnitDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[DocumentaryUnitDescriptionF] {

  // This should have one logical object
  val item: Option[DocumentaryUnit] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(DocumentaryUnit(_))

  lazy val formable = Json.toJson(e).as[DocumentaryUnitDescriptionF]
  lazy val formableOpt = Json.toJson(e).asOpt[DocumentaryUnitDescriptionF]
}