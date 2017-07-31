package models

import models.base._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import defines.EntityType
import models.json._
import eu.ehri.project.definitions.Ontology
import utils.forms._
import play.api.data.Form
import play.api.data.Forms._
import models.base.Description._
import services.data.Writable

case class IsadGIdentity(
  name: String,
  parallelFormsOfName: Option[Seq[String]] = None,
  identifier: Option[String] = None,
  ref: Option[String] = None,
  `abstract`: Option[String] = None,
  @models.relation(Ontology.ENTITY_HAS_DATE)
  dates: Seq[DatePeriodF] = Nil,
  unitDates: Option[Seq[String]] = None,
  levelOfDescription: Option[String] = None,
  physicalLocation: Option[Seq[String]] = None,
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
  languageOfMaterials: Option[Seq[String]] = None,
  scriptOfMaterials: Option[Seq[String]] = None,
  physicalCharacteristics: Option[String] = None,
  findingAids: Option[Seq[String]] = None
) extends AttributeSet

case class IsadGMaterials(
  locationOfOriginals: Option[Seq[String]] = None,
  locationOfCopies: Option[Seq[String]] = None,
  relatedUnitsOfDescription: Option[Seq[String]] = None,
  separatedUnitsOfDescription: Option[Seq[String]] = None,
  publicationNote: Option[String] = None
) extends AttributeSet


case class IsadGControl(
  archivistNote: Option[String] = None,
  sources: Option[Seq[String]] = None,
  rulesAndConventions: Option[String] = None,
  datesOfDescriptions: Option[String] = None,
  processInfo: Option[Seq[String]] = None
)

object DocumentaryUnitDescriptionF {

  import Entity._
  import models.IsadG._
  import Ontology._

  implicit val documentaryUnitDescriptionFormat: Format[DocumentaryUnitDescriptionF] = (
    (__ \ TYPE).formatIfEquals(EntityType.DocumentaryUnitDescription) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ LANG_CODE).format[String] and
    __.format[IsadGIdentity]((
      (__ \ DATA \ TITLE).format[String] and
      (__ \ DATA \ PARALLEL_FORMS_OF_NAME).formatSeqOrSingleNullable[String] and
      (__ \ DATA \ IDENTIFIER).formatNullable[String] and
      (__ \ DATA \ REF).formatNullable[String] and
      (__ \ DATA \ ABSTRACT).formatNullable[String] and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).formatSeqOrEmpty[DatePeriodF] and
      (__ \ DATA \ UNIT_DATES).formatSeqOrSingleNullable[String] and
      (__ \ DATA \ LEVEL_OF_DESCRIPTION).formatNullable[String] and
      (__ \ DATA \ PHYSICAL_LOCATION).formatSeqOrSingleNullable[String] and
      (__ \ DATA \ EXTENT_MEDIUM).formatNullable[String]
    )(IsadGIdentity.apply, unlift(IsadGIdentity.unapply))) and
    (__ \ DATA).format[IsadGContext]((
      (__ \ ADMIN_BIOG).formatNullable[String] and
      (__ \ ARCH_HIST).formatNullable[String] and
      (__ \ ACQUISITION).formatNullable[String]
    )(IsadGContext.apply, unlift(IsadGContext.unapply))) and
    (__ \ DATA).format[IsadGContent]((
      (__ \ SCOPE_CONTENT).formatNullable[String] and
      (__ \ APPRAISAL).formatNullable[String] and
      (__ \ ACCRUALS).formatNullable[String] and
      (__ \ SYS_ARR).formatNullable[String]
    )(IsadGContent.apply, unlift(IsadGContent.unapply))) and
    (__ \ DATA).format[IsadGConditions]((
      (__ \ ACCESS_COND).formatNullable[String] and
      (__ \ REPROD_COND).formatNullable[String] and
      (__ \ LANG_MATERIALS).formatSeqOrSingleNullable[String] and
      (__ \ SCRIPT_MATERIALS).formatSeqOrSingleNullable[String] and
      (__ \ PHYSICAL_CHARS).formatNullable[String] and
      (__ \ FINDING_AIDS).formatSeqOrSingleNullable[String]
    )(IsadGConditions.apply, unlift(IsadGConditions.unapply))) and
    (__ \ DATA).format[IsadGMaterials]((
      (__ \ LOCATION_ORIGINALS).formatSeqOrSingleNullable[String] and
      (__ \ LOCATION_COPIES).formatSeqOrSingleNullable[String] and
      (__ \ RELATED_UNITS).formatSeqOrSingleNullable[String] and
      (__ \ SEPARATED_UNITS).formatSeqOrSingleNullable[String] and
      (__ \ PUBLICATION_NOTE).formatNullable[String]
    )(IsadGMaterials.apply, unlift(IsadGMaterials.unapply))) and
    (__ \ DATA \ NOTES).formatSeqOrSingleNullable[String] and
    (__ \ DATA).format[IsadGControl]((
      (__ \ ARCHIVIST_NOTE).formatNullable[String] and
      (__ \ SOURCES).formatSeqOrSingleNullable[String] and
      (__ \ RULES_CONVENTIONS).formatNullable[String] and
      (__ \ DATES_DESCRIPTIONS).formatNullable[String] and
      (__ \ PROCESS_INFO).formatSeqOrSingleNullable[String]
    )(IsadGControl.apply, unlift(IsadGControl.unapply))) and
    (__ \ DATA \ CREATION_PROCESS).formatWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).formatSeqOrEmpty[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_MAINTENANCE_EVENT).formatSeqOrEmpty[MaintenanceEventF] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).formatSeqOrEmpty[Entity]
  )(DocumentaryUnitDescriptionF.apply, unlift(DocumentaryUnitDescriptionF.unapply))

  implicit object Converter extends Writable[DocumentaryUnitDescriptionF] {
    val restFormat: Format[DocumentaryUnitDescriptionF] = documentaryUnitDescriptionFormat
  }
}

case class DocumentaryUnitDescriptionF(
  isA: EntityType.Value = EntityType.DocumentaryUnitDescription,
  id: Option[String] = None,
  languageCode: String,
  identity: IsadGIdentity,
  context: IsadGContext = IsadGContext(),
  content: IsadGContent = IsadGContent(),
  conditions: IsadGConditions = IsadGConditions(),
  materials: IsadGMaterials = IsadGMaterials(),
  notes: Option[Seq[String]] = None,
  control: IsadGControl = IsadGControl(),
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  @models.relation(Ontology.HAS_ACCESS_POINT)
  accessPoints: Seq[AccessPointF] = Nil,
  @models.relation(Ontology.HAS_MAINTENANCE_EVENT)
  maintenanceEvents: Seq[MaintenanceEventF] = Nil,
  @models.relation(Ontology.HAS_UNKNOWN_PROPERTY)
  unknownProperties: Seq[Entity] = Nil
) extends Model with Persistable with Description with Temporal {
  import models.IsadG._

  def name: String = identity.name
  def dates: Seq[DatePeriodF] = identity.dates

  def displayText: Option[String] = identity.`abstract` orElse content.scopeAndContent

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
    FINDING_AIDS -> conditions.findingAids.map(_.mkString("\n")),
    LOCATION_ORIGINALS -> materials.locationOfOriginals.map(_.mkString("\n")),
    LOCATION_COPIES -> materials.locationOfCopies.map(_.mkString("\n")),
    RELATED_UNITS -> materials.relatedUnitsOfDescription.map(_.mkString("\n")),
    SEPARATED_UNITS -> materials.separatedUnitsOfDescription.map(_.mkString("\n")),
    PUBLICATION_NOTE -> materials.publicationNote,
    ARCHIVIST_NOTE -> control.archivistNote,
    SOURCES -> control.sources.map(_.mkString("\n")),
    RULES_CONVENTIONS -> control.rulesAndConventions,
    DATES_DESCRIPTIONS -> control.datesOfDescriptions,
    PROCESS_INFO -> control.processInfo.map(_.mkString("\n"))
  )
}

object DocumentaryUnitDescription {
  import models.IsadG._
  import Entity._
  import defines.EnumUtils.enumMapping

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.DocumentaryUnitDescription),
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      IDENTITY_AREA -> mapping(
        TITLE -> nonEmptyText,
        PARALLEL_FORMS_OF_NAME -> optional(seq(nonEmptyText)),
        Entity.IDENTIFIER -> optional(nonEmptyText),
        REF -> optional(text),
        ABSTRACT -> optional(nonEmptyText),
        DATES -> seq(DatePeriod.form.mapping),
        UNIT_DATES -> optional(seq(nonEmptyText)),
        LEVEL_OF_DESCRIPTION -> optional(text),
        PHYSICAL_LOCATION -> optional(seq(nonEmptyText)),
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
        LANG_MATERIALS -> optional(seq(nonEmptyText)),
        SCRIPT_MATERIALS -> optional(seq(nonEmptyText)),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> optional(seq(nonEmptyText))
      )(IsadGConditions.apply)(IsadGConditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> optional(seq(nonEmptyText)),
        LOCATION_COPIES -> optional(seq(nonEmptyText)),
        RELATED_UNITS -> optional(seq(nonEmptyText)),
        SEPARATED_UNITS -> optional(seq(nonEmptyText)),
        PUBLICATION_NOTE -> optional(text)
      )(IsadGMaterials.apply)(IsadGMaterials.unapply),
      NOTES -> optional(seq(nonEmptyText)),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        SOURCES -> optional(seq(nonEmptyText)),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text),
        PROCESS_INFO -> optional(seq(nonEmptyText))
      )(IsadGControl.apply)(IsadGControl.unapply),
      CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
      ACCESS_POINTS -> seq(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> seq(MaintenanceEventF.form.mapping),
      UNKNOWN_DATA -> seq(entity)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}
