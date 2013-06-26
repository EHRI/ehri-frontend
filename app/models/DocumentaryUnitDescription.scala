package models

import models.base._
import play.api.libs.json.{JsValue, Json}
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}

case class IsadGContext(
  adminBiogHistory: Option[String] = None,
  archivalHistory: Option[String] = None,
  acquisition: Option[String] = None
) extends AttributeSet

case class IsadGContent(
  scopeAndContent: Option[String] = None,
  appraisal: Option[String] = None,
  accruals: Option[String] = None,
  systemOfArrangement: Option[String] = None
) extends AttributeSet

case class IsadGConditions(
  conditionsOfAccess: Option[String] = None,
  conditionsOfReproduction: Option[String] = None,
  languageOfMaterials: Option[List[String]] = None,
  scriptOfMaterials: Option[List[String]] = None,
  physicalCharacteristics: Option[String] = None,
  findingAids: Option[String] = None
) extends AttributeSet

case class IsadGMaterials(
  locationOfOriginals: Option[String] = None,
  locationOfCopies: Option[String] = None,
  relatedUnitsOfDescription: Option[String] = None,
  publicationNote: Option[String] = None
) extends AttributeSet


case class IsadGControl(
  archivistNote: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  datesOfDescriptions: Option[String] = None
)

object DocumentaryUnitDescriptionF {

  //lazy implicit val documentaryUnitDescriptionFormat = json.IsadGFormat.restFormat

  implicit object Converter extends RestConvertable[DocumentaryUnitDescriptionF] with ClientConvertable[DocumentaryUnitDescriptionF] {
    lazy val restFormat = models.json.rest.isadGFormat
    lazy val clientFormat = models.json.client.isadGFormat
  }
}

case class DocumentaryUnitDescriptionF(
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  `abstract`: Option[String],
  @Annotations.Relation(TemporalEntity.DATE_REL)
  dates: List[DatePeriodF] = Nil,
  levelOfDescription: Option[IsadG.LevelOfDescription.Value] = None,
  extentAndMedium: Option[String] = None,
  context: IsadGContext,
  content: IsadGContent,
  conditions: IsadGConditions,
  materials: IsadGMaterials,
  notes: Option[List[String]] = None,
  control: IsadGControl,
  accessPoints: List[AccessPointF]
) extends Model with Persistable with Description

/*
case class DocumentaryUnitDescription(val e: Entity)
  extends Description
  with TemporalEntity
  with Formable[DocumentaryUnitDescriptionF] {

  import IsadG._

  // This should have one logical object
  val item: Option[DocumentaryUnit] = e.relations(DescribedEntity.DESCRIBES_REL).headOption.map(DocumentaryUnit(_))

  // What to display on search listings
  lazy val displayText: Option[String] = e.stringProperty(ABSTRACT) orElse e.stringProperty(SCOPE_CONTENT)

  lazy val formable = Json.toJson(e).as[DocumentaryUnitDescriptionF](json.IsadGFormat.restFormat)
  lazy val formableOpt = Json.toJson(e).asOpt[DocumentaryUnitDescriptionF](json.IsadGFormat.restFormat)
}
*/
