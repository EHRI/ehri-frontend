package models

import models.base._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import defines.EntityType
import models.json._
import eu.ehri.project.definitions.Ontology
import models.forms._
import play.api.data.Form
import play.api.data.Forms._
import defines.EnumUtils._
import models.Isdiah._

case class IsadGIdentity(
  name: String,
  parallelFormsOfName: Option[List[String]] = None,
  identifier: Option[String] = None,
  ref: Option[String] = None,
  `abstract`: Option[String] = None,
  @Annotations.Relation(Ontology.ENTITY_HAS_DATE)
  dates: List[DatePeriodF] = Nil,
  levelOfDescription: Option[String] = None,
  physicalLocation: Option[List[String]] = None,
  extentAndMedium: Option[String] = None
) extends AttributeSet

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
  locationOfOriginals: Option[List[String]] = None,
  locationOfCopies: Option[List[String]] = None,
  relatedUnitsOfDescription: Option[String] = None,
  publicationNote: Option[String] = None
) extends AttributeSet


case class IsadGControl(
  archivistNote: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  datesOfDescriptions: Option[String] = None,
  provenance: Option[String] = None
)

object DocumentaryUnitDescriptionF {

  import Entity._
  import IsadG._
  import Ontology._

  implicit val documentaryUnitDescriptionWrites = new Writes[DocumentaryUnitDescriptionF] {
    def writes(d: DocumentaryUnitDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identity.identifier,
          TITLE -> d.identity.name,
          PARALLEL_FORMS_OF_NAME -> d.identity.parallelFormsOfName,
          REF -> d.identity.ref,
          ABSTRACT -> d.identity.`abstract`,
          LANG_CODE -> d.languageCode,
          LEVEL_OF_DESCRIPTION -> d.identity.levelOfDescription,
          PHYSICAL_LOCATION -> d.identity.physicalLocation,
          EXTENT_MEDIUM -> d.identity.extentAndMedium,
          ADMIN_BIOG -> d.context.biographicalHistory,
          ARCH_HIST -> d.context.archivalHistory,
          ACQUISITION -> d.context.acquisition,
          SCOPE_CONTENT -> d.content.scopeAndContent,
          APPRAISAL -> d.content.appraisal,
          ACCRUALS -> d.content.accruals,
          SYS_ARR -> d.content.systemOfArrangement,
          ACCESS_COND -> d.conditions.conditionsOfAccess,
          REPROD_COND -> d.conditions.conditionsOfReproduction,
          LANG_MATERIALS -> d.conditions.languageOfMaterials,
          SCRIPT_MATERIALS -> d.conditions.scriptOfMaterials,
          PHYSICAL_CHARS -> d.conditions.physicalCharacteristics,
          FINDING_AIDS -> d.conditions.findingAids,
          LOCATION_ORIGINALS -> d.materials.locationOfOriginals,
          LOCATION_COPIES -> d.materials.locationOfCopies,
          RELATED_UNITS -> d.materials.relatedUnitsOfDescription,
          PUBLICATION_NOTE -> d.materials.publicationNote,
          NOTES -> d.notes,
          ARCHIVIST_NOTE -> d.control.archivistNote,
          RULES_CONVENTIONS -> d.control.rulesAndConventions,
          DATES_DESCRIPTIONS -> d.control.datesOfDescriptions,
          PROVENANCE -> d.control.provenance
        ),
        RELATIONSHIPS -> Json.obj(
          ENTITY_HAS_DATE -> Json.toJson(d.dates.map(Json.toJson(_)).toSeq),
          HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          HAS_MAINTENANCE_EVENT -> Json.toJson(d.maintenanceEvents.map(Json.toJson(_)).toSeq),
          HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  private implicit val levelOfDescriptionReads = enumReads(LevelOfDescription)

  implicit val documentaryUnitDescriptionReads: Reads[DocumentaryUnitDescriptionF] = (
    (__ \ TYPE).readIfEquals(EntityType.DocumentaryUnitDescription) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LANG_CODE).read[String] and
    __.read[IsadGIdentity]((
      (__ \ DATA \ TITLE).read[String] and
      (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readListOrSingleNullable[String] and
      (__ \ DATA \ IDENTIFIER).readNullable[String] and
      (__ \ DATA \ REF).readNullable[String] and
      (__ \ DATA \ ABSTRACT).readNullable[String] and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).nullableListReads[DatePeriodF] and
      (__ \ DATA \ LEVEL_OF_DESCRIPTION).readNullable[String] and
      (__ \ DATA \ PHYSICAL_LOCATION).readListOrSingleNullable[String] and
      (__ \ DATA \ EXTENT_MEDIUM).readNullable[String]
    )(IsadGIdentity.apply _)) and
    (__ \ DATA).read[IsadGContext]((
      (__ \ ADMIN_BIOG).readNullable[String] and
      (__ \ ARCH_HIST).readNullable[String] and
      (__ \ ACQUISITION).readNullable[String]
    )(IsadGContext.apply _)) and
    (__ \ DATA).read[IsadGContent]((
      (__ \ SCOPE_CONTENT).readNullable[String] and
      (__ \ APPRAISAL).readNullable[String] and
      (__ \ ACCRUALS).readNullable[String] and
      (__ \ SYS_ARR).readNullable[String]
    )(IsadGContent.apply _)) and
    (__ \ DATA).read[IsadGConditions]((
      (__ \ ACCESS_COND).readNullable[String] and
      (__ \ REPROD_COND).readNullable[String] and
      (__ \ LANG_MATERIALS).readListOrSingleNullable[String] and
      (__ \ SCRIPT_MATERIALS).readListOrSingleNullable[String] and
      (__ \ PHYSICAL_CHARS).readNullable[String] and
      (__ \ FINDING_AIDS).readNullable[String]
    )(IsadGConditions.apply _)) and
    (__ \ DATA).read[IsadGMaterials]((
      (__ \ LOCATION_ORIGINALS).readListOrSingleNullable[String] and
      (__ \ LOCATION_COPIES).readListOrSingleNullable[String] and
      (__ \ RELATED_UNITS).readNullable[String] and
      (__ \ PUBLICATION_NOTE).readNullable[String]
    )(IsadGMaterials.apply _)) and
    (__ \ DATA \ NOTES).readListOrSingleNullable[String] and
    (__ \ DATA).read[IsadGControl]((
      (__ \ ARCHIVIST_NOTE).readNullable[String] and
      (__ \ RULES_CONVENTIONS).readNullable[String] and
      (__ \ DATES_DESCRIPTIONS).readNullable[String] and
      (__ \ PROVENANCE).readNullable[String]
    )(IsadGControl.apply _)) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).nullableListReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_MAINTENANCE_EVENT).nullableListReads[Entity] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).nullableListReads[Entity]
  )(DocumentaryUnitDescriptionF.apply _)

  implicit val documentaryUnitDescriptionFormat: Format[DocumentaryUnitDescriptionF]
  = Format(documentaryUnitDescriptionReads, documentaryUnitDescriptionWrites)

  implicit object Converter extends RestConvertable[DocumentaryUnitDescriptionF] with ClientConvertable[DocumentaryUnitDescriptionF] {
    val restFormat = documentaryUnitDescriptionFormat

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
  maintenanceEvents: List[Entity] = Nil,
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
    PHYSICAL_LOCATION -> identity.physicalLocation.map(_.mkString("\n")),
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
    LOCATION_ORIGINALS -> materials.locationOfOriginals.map(_.mkString("\n")),
    LOCATION_COPIES -> materials.locationOfCopies.map(_.mkString("\n")),
    RELATED_UNITS -> materials.relatedUnitsOfDescription,
    PUBLICATION_NOTE -> materials.publicationNote,
    ARCHIVIST_NOTE -> control.archivistNote,
    RULES_CONVENTIONS -> control.rulesAndConventions,
    DATES_DESCRIPTIONS -> control.datesOfDescriptions,
    PROVENANCE -> control.provenance
  )
}

object DocumentaryUnitDescription {
  import IsadG._
  import Entity._

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.DocumentaryUnitDescription),
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      IDENTITY_AREA -> mapping(
        TITLE -> nonEmptyText,
        PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
        Entity.IDENTIFIER -> optional(nonEmptyText),
        REF -> optional(text),
        ABSTRACT -> optional(nonEmptyText),
        DATES -> list(DatePeriod.form.mapping),
        LEVEL_OF_DESCRIPTION -> optional(text),
        PHYSICAL_LOCATION -> optional(list(nonEmptyText)),
        EXTENT_MEDIUM -> optional(nonEmptyText)
      )(IsadGIdentity.apply)(IsadGIdentity.unapply),
      CONTEXT_AREA -> mapping(
        ADMIN_BIOG -> optional(text),
        ARCH_HIST -> optional(text),
        ACQUISITION -> optional(text)
      )(IsadGContext.apply)(IsadGContext.unapply),
      CONTENT_AREA -> mapping(
        SCOPE_CONTENT -> optional(text),
        APPRAISAL -> optional(text),
        ACCRUALS -> optional(text),
        SYS_ARR -> optional(text)
      )(IsadGContent.apply)(IsadGContent.unapply),
      CONDITIONS_AREA -> mapping(
        ACCESS_COND -> optional(text),
        REPROD_COND -> optional(text),
        LANG_MATERIALS -> optional(list(nonEmptyText)),
        SCRIPT_MATERIALS -> optional(list(nonEmptyText)),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> optional(text)
      )(IsadGConditions.apply)(IsadGConditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> optional(list(nonEmptyText)),
        LOCATION_COPIES -> optional(list(nonEmptyText)),
        RELATED_UNITS -> optional(text),
        PUBLICATION_NOTE -> optional(text)
      )(IsadGMaterials.apply)(IsadGMaterials.unapply),
      NOTES -> optional(list(nonEmptyText)),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text),
        PROVENANCE -> optional(text)
      )(IsadGControl.apply)(IsadGControl.unapply),
      ACCESS_POINTS -> list(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> list(entity),
      UNKNOWN_DATA -> list(entity)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}