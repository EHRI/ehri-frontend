package models

import models.base._
import play.api.libs.json.{JsObject, JsValue, Json}
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import eu.ehri.project.definitions.Ontology

case class IsadGIdentity(
  name: String,
  parallelFormsOfName: Option[List[String]] = None,
  identifier: Option[String] = None,
  ref: Option[String] = None,
  `abstract`: Option[String] = None,
  @Annotations.Relation(Ontology.ENTITY_HAS_DATE)
  dates: List[DatePeriodF] = Nil,
  levelOfDescription: Option[String] = None,
  extentAndMedium: Option[String] = None
)

case class IsadGContext(
  biographicalHistory: Option[String] = None,
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
    private implicit val isadGIdentityFormat = Json.format[IsadGIdentity]
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
  identity: IsadGIdentity,
  context: IsadGContext,
  content: IsadGContent,
  conditions: IsadGConditions,
  materials: IsadGMaterials,
  notes: Option[List[String]] = None,
  control: IsadGControl,
  accessPoints: List[AccessPointF] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description with Temporal {
  import IsadG._

  def name = identity.name
  def dates = identity.dates

  def displayText = identity.`abstract` orElse content.scopeAndContent

  def externalLink(item: DocumentaryUnit): Option[String] = identity.ref orElse {
    for {
      holder <- item.holder
      pattern <- holder.model.urlPattern
    } yield pattern.replaceAll("\\{identifier\\}", item.model.identifier)
  }

  def toSeq = Seq(
    ABSTRACT -> identity.`abstract`,
    LEVEL_OF_DESCRIPTION -> identity.levelOfDescription,
    EXTENT_MEDIUM -> identity.extentAndMedium,
    ADMIN_BIOG -> context.biographicalHistory,
    ARCH_HIST -> context.archivalHistory,
    ACQUISITION -> context.acquisition,
    SCOPE_CONTENT -> content.scopeAndContent,
    APPRAISAL -> content.appraisal,
    ACCRUALS -> content.accruals,
    SYS_ARR -> content.systemOfArrangement,
    ACCESS_COND -> conditions.conditionsOfAccess,
    REPROD_COND -> conditions.conditionsOfReproduction,
    PHYSICAL_CHARS -> conditions.physicalCharacteristics,
    FINDING_AIDS -> conditions.findingAids,
    LOCATION_ORIGINALS -> materials.locationOfOriginals,
    LOCATION_COPIES -> materials.locationOfCopies,
    RELATED_UNITS -> materials.relatedUnitsOfDescription,
    PUBLICATION_NOTE -> materials.publicationNote,
    ARCHIVIST_NOTE -> control.archivistNote,
    RULES_CONVENTIONS -> control.rulesAndConventions,
    DATES_DESCRIPTIONS -> control.datesOfDescriptions
  )
}
