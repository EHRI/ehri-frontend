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
import backend.{Entity, Readable, BackendWriteable}
import Description._

case class IsdiahDetails(
  history: Option[String] = None,
  generalContext: Option[String] = None,
  mandates: Option[String] = None,
  administrativeStructure: Option[String] = None,
  records: Option[String] = None,
  buildings: Option[String] = None,
  holdings: Option[String] = None,
  findingAids: Option[String] = None
) extends AttributeSet

case class IsdiahAccess(
  openingTimes: Option[String] = None,
  conditions: Option[String] = None,
  accessibility: Option[String] = None
) extends AttributeSet

case class IsdiahServices(
  researchServices: Option[String] = None,
  reproductionServices: Option[String] = None,
  publicAreas: Option[String] = None
) extends AttributeSet

case class IsdiahControl(
  descriptionIdentifier: Option[String] = None,
  institutionIdentifier: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  status: Option[String] = None,
  levelOfDetail: Option[String] = None,
  datesCDR: Option[String] = None,
  languages: Option[Seq[String]] = None,
  scripts: Option[Seq[String]] = None,
  sources: Option[Seq[String]] = None,
  maintenanceNotes: Option[String] = None
) extends AttributeSet

object RepositoryDescriptionF {

  import Entity._
  import Isdiah._
  import eu.ehri.project.definitions.Ontology._

  implicit val repositoryDescriptionWrites = new Writes[RepositoryDescriptionF] {
    def writes(d: RepositoryDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          AUTHORIZED_FORM_OF_NAME -> d.name,
          LANG_CODE -> d.languageCode,
          OTHER_FORMS_OF_NAME -> d.otherFormsOfName,
          PARALLEL_FORMS_OF_NAME -> d.parallelFormsOfName,
          HISTORY -> d.details.history,
          GEOCULTURAL_CONTEXT -> d.details.generalContext,
          MANDATES -> d.details.mandates,
          ADMINISTRATIVE_STRUCTURE -> d.details.administrativeStructure,
          RECORDS -> d.details.records,
          BUILDINGS -> d.details.buildings,
          HOLDINGS -> d.details.holdings,
          FINDING_AIDS -> d.details.findingAids,
          OPENING_TIMES -> d.access.openingTimes,
          CONDITIONS -> d.access.conditions,
          ACCESSIBILITY -> d.access.accessibility,
          RESEARCH_SERVICES -> d.services.researchServices,
          REPROD_SERVICES -> d.services.reproductionServices,
          PUBLIC_AREAS -> d.services.publicAreas,
          DESCRIPTION_IDENTIFIER -> d.control.descriptionIdentifier,
          INSTITUTION_IDENTIFIER -> d.control.institutionIdentifier,
          RULES_CONVENTIONS -> d.control.rulesAndConventions,
          STATUS -> d.control.status,
          LEVEL_OF_DETAIL -> d.control.levelOfDetail,
          DATES_CVD -> d.control.datesCDR,
          LANGUAGES_USED -> d.control.languages,
          SCRIPTS_USED -> d.control.scripts,
          SOURCES -> d.control.sources,
          MAINTENANCE_NOTES -> d.control.maintenanceNotes,
          CREATION_PROCESS -> d.creationProcess
        ),
        RELATIONSHIPS -> Json.obj(
          ENTITY_HAS_ADDRESS -> Json.toJson(d.addresses.map(Json.toJson(_)).toSeq),
          HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          HAS_MAINTENANCE_EVENT -> Json.toJson(d.maintenanceEvents.map(Json.toJson(_)).toSeq),
          HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val repositoryDescriptionReads: Reads[RepositoryDescriptionF] = (
    (__ \ TYPE).readIfEquals(EntityType.RepositoryDescription) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LANG_CODE).read[String] and
    (__ \ DATA \ AUTHORIZED_FORM_OF_NAME).read[String] and
    (__ \ DATA \ OTHER_FORMS_OF_NAME).readSeqOrSingleNullable[String] and
    (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readSeqOrSingleNullable[String] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_ADDRESS).nullableSeqReads[AddressF] and
    (__ \ DATA).read[IsdiahDetails]((
      (__ \ HISTORY).readNullable[String] and
      (__ \ GEOCULTURAL_CONTEXT).readNullable[String] and
      (__ \ MANDATES).readNullable[String] and
      (__ \ ADMINISTRATIVE_STRUCTURE).readNullable[String] and
      (__ \ RECORDS).readNullable[String] and
      (__ \ BUILDINGS).readNullable[String] and
      (__ \ HOLDINGS).readNullable[String] and
      (__ \ FINDING_AIDS).readNullable[String]
    )(IsdiahDetails.apply _)) and
    (__ \ DATA).read[IsdiahAccess]((
      (__ \ OPENING_TIMES).readNullable[String] and
      (__ \ CONDITIONS).readNullable[String] and
      (__ \ ACCESSIBILITY).readNullable[String]
    )(IsdiahAccess.apply _)) and
    (__ \ DATA).read[IsdiahServices]((
      (__ \ RESEARCH_SERVICES).readNullable[String] and
      (__ \ REPROD_SERVICES).readNullable[String] and
      (__ \ PUBLIC_AREAS).readNullable[String]
    )(IsdiahServices.apply _)) and
    (__ \ DATA).read[IsdiahControl]((
      (__ \ DESCRIPTION_IDENTIFIER).readNullable[String] and
      (__ \ INSTITUTION_IDENTIFIER).readNullable[String] and
      (__ \ RULES_CONVENTIONS).readNullable[String] and
      (__ \ STATUS).readNullable[String] and
      (__ \ LEVEL_OF_DETAIL).readNullable[String] and
      (__ \ DATES_CVD).readNullable[String] and
      (__ \ LANGUAGES_USED).readNullable[Seq[String]] and
      (__ \ SCRIPTS_USED).readSeqOrSingleNullable[String] and
      (__ \ SOURCES).readSeqOrSingleNullable[String] and
      (__ \ MAINTENANCE_NOTES).readNullable[String]
    )(IsdiahControl.apply _)) and
    (__ \ DATA \ CREATION_PROCESS).readWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).nullableSeqReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_MAINTENANCE_EVENT).nullableSeqReads[Entity] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).nullableSeqReads[Entity]
  )(RepositoryDescriptionF.apply _)

  implicit object Converter extends Readable[RepositoryDescriptionF] with BackendWriteable[RepositoryDescriptionF] {
    val restReads = repositoryDescriptionReads
    val restFormat = Format(repositoryDescriptionReads, repositoryDescriptionWrites)
  }
}


case class RepositoryDescriptionF(
  isA: EntityType.Value = EntityType.RepositoryDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  otherFormsOfName: Option[Seq[String]] = None,
  parallelFormsOfName: Option[Seq[String]] = None,
  @models.relation(Ontology.ENTITY_HAS_ADDRESS) addresses: Seq[AddressF] = Nil,
  details: IsdiahDetails,
  access: IsdiahAccess,
  services: IsdiahServices,
  control: IsdiahControl,
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  accessPoints: Seq[AccessPointF] = Nil,
  maintenanceEvents: Seq[Entity] = Nil,
  unknownProperties: Seq[Entity] = Nil
) extends Model with Persistable with Description {

  import Isdiah._

  def displayText = details.history orElse details.generalContext

  def toSeq = Seq(
    HISTORY -> details.history,
    GEOCULTURAL_CONTEXT -> details.generalContext,
    MANDATES -> details.mandates,
    ADMINISTRATIVE_STRUCTURE -> details.administrativeStructure,
    RECORDS -> details.records,
    BUILDINGS -> details.buildings,
    HOLDINGS -> details.holdings,
    FINDING_AIDS -> details.findingAids,
    OPENING_TIMES -> access.openingTimes,
    CONDITIONS -> access.conditions,
    ACCESSIBILITY -> access.accessibility,
    RESEARCH_SERVICES -> services.researchServices,
    REPROD_SERVICES -> services.reproductionServices,
    PUBLIC_AREAS -> services.publicAreas,
    DESCRIPTION_IDENTIFIER -> control.descriptionIdentifier,
    INSTITUTION_IDENTIFIER -> control.institutionIdentifier,
    RULES_CONVENTIONS -> control.rulesAndConventions,
    STATUS -> control.status,
    LEVEL_OF_DETAIL -> control.levelOfDetail,
    DATES_CVD -> control.datesCDR,
    MAINTENANCE_NOTES -> control.maintenanceNotes
  )
}

object RepositoryDescription {
  import Isdiah._
  import Entity._
  import defines.EnumUtils.enumMapping

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.RepositoryDescription),
      ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      AUTHORIZED_FORM_OF_NAME -> text,
      OTHER_FORMS_OF_NAME -> optional(seq(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(seq(nonEmptyText)),
      ADDRESS_AREA -> seq(Address.form.mapping),
      DESCRIPTION_AREA -> mapping(
        HISTORY -> optional(nonEmptyText),
        GEOCULTURAL_CONTEXT -> optional(nonEmptyText),
        MANDATES -> optional(nonEmptyText),
        ADMINISTRATIVE_STRUCTURE -> optional(nonEmptyText),
        RECORDS -> optional(nonEmptyText),
        BUILDINGS -> optional(nonEmptyText),
        HOLDINGS -> optional(nonEmptyText),
        FINDING_AIDS -> optional(nonEmptyText)
      )(IsdiahDetails.apply)(IsdiahDetails.unapply),
      ACCESS_AREA -> mapping(
        OPENING_TIMES -> optional(text),
        CONDITIONS -> optional(text),
        ACCESSIBILITY -> optional(text)
      )(IsdiahAccess.apply)(IsdiahAccess.unapply),
      SERVICES_AREA -> mapping(
        RESEARCH_SERVICES -> optional(text),
        REPROD_SERVICES -> optional(text),
        PUBLIC_AREAS -> optional(text)
      )(IsdiahServices.apply)(IsdiahServices.unapply),
      CONTROL_AREA -> mapping(
        DESCRIPTION_IDENTIFIER -> optional(text),
        INSTITUTION_IDENTIFIER -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        STATUS -> optional(text),
        LEVEL_OF_DETAIL -> optional(text),
        DATES_CVD -> optional(text),
        LANGUAGES_USED -> optional(seq(nonEmptyText)),
        SCRIPTS_USED -> optional(seq(nonEmptyText)),
        SOURCES -> optional(seq(nonEmptyText)),
        MAINTENANCE_NOTES -> optional(text)
      )(IsdiahControl.apply)(IsdiahControl.unapply),
      CREATION_PROCESS -> default(enumMapping(CreationProcess), CreationProcess.Manual),
      ACCESS_POINTS -> seq(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> seq(entity),
      UNKNOWN_DATA -> seq(entity)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}

