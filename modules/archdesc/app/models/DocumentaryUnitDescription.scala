package models

import models.base._
import play.api.libs.json.{JsObject, JsValue, Json}
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import eu.ehri.project.definitions.Ontology

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

  implicit object Converter extends RestConvertable[DocumentaryUnitDescriptionF] with ClientConvertable[DocumentaryUnitDescriptionF] {
    val restFormat = models.json.IsadGFormat.restFormat

    private implicit val entityFormat = json.entityFormat
    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val datePeriodFormat = DatePeriodF.Converter.clientFormat
    private implicit val isadGContextFormat = Json.format[IsadGContext]
    private implicit val isadGContentFormat = Json.format[IsadGContent]
    private implicit val isadGConditionsFormat = Json.format[IsadGConditions]
    private implicit val isadGMaterialsFormat = Json.format[IsadGMaterials]
    private implicit val isadGControlFormat = Json.format[IsadGControl]
    val clientFormat = Json.format[DocumentaryUnitDescriptionF]
  }
}

case class DocumentaryUnitDescriptionF(
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  `abstract`: Option[String],
  @Annotations.Relation(Ontology.ENTITY_HAS_DATE)
  dates: List[DatePeriodF] = Nil,
  levelOfDescription: Option[String] = None,
  extentAndMedium: Option[String] = None,
  context: IsadGContext,
  content: IsadGContent,
  conditions: IsadGConditions,
  materials: IsadGMaterials,
  notes: Option[List[String]] = None,
  control: IsadGControl,
  accessPoints: List[AccessPointF] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description with Temporal {

  def displayText = `abstract` orElse content.scopeAndContent
}
