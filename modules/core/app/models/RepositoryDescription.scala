package models

import models.base._
import play.api.libs.json.Json
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import eu.ehri.project.definitions.Ontology
import models.forms._

private[models] case class IsdiahDetails(
  history: Option[String] = None,
  generalContext: Option[String] = None,
  mandates: Option[String] = None,
  administrativeStructure: Option[String] = None,
  records: Option[String] = None,
  buildings: Option[String] = None,
  holdings: Option[String] = None,
  findingAids: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahAccess(
  openingTimes: Option[String] = None,
  conditions: Option[String] = None,
  accessibility: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahServices(
  researchServices: Option[String] = None,
  reproductionServices: Option[String] = None,
  publicAreas: Option[String] = None
) extends AttributeSet

private[models] case class IsdiahControl(
  descriptionIdentifier: Option[String] = None,
  institutionIdentifier: Option[String] = None,
  rulesAndConventions: Option[String] = None,
  status: Option[String] = None,
  levelOfDetail: Option[String] = None,
  datesCDR: Option[String] = None,
  languages: Option[List[String]] = None,
  scripts: Option[List[String]] = None,
  sources: Option[List[String]] = None,
  maintenanceNotes: Option[String] = None
) extends AttributeSet

object RepositoryDescriptionF {

  implicit object Converter extends RestConvertable[RepositoryDescriptionF] with ClientConvertable[RepositoryDescriptionF] {
    val restFormat = models.json.RepositoryDescriptionFormat.restFormat

    private implicit val entityFormat = json.entityFormat
    private implicit val addressFormat = AddressF.Converter.clientFormat
    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val isdiahDetailsFormat = Json.format[IsdiahDetails]
    private implicit val isdiahAccessFormat = Json.format[IsdiahAccess]
    private implicit val isdiahServicesFormat = Json.format[IsdiahServices]
    private implicit val isdiahControlFormat = Json.format[IsdiahControl]
    val clientFormat = Json.format[RepositoryDescriptionF]
  }
}


case class RepositoryDescriptionF(
  isA: EntityType.Value = EntityType.RepositoryDescription,
  id: Option[String],
  languageCode: String,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,
  @Annotations.Relation(Ontology.ENTITY_HAS_ADDRESS) addresses: List[AddressF] = Nil,
  details: IsdiahDetails,
  access: IsdiahAccess,
  services: IsdiahServices,
  control: IsdiahControl,
  accessPoints: List[AccessPointF] = Nil,
  maintenanceEvents: List[Entity] = Nil,
  unknownProperties: List[Entity] = Nil
) extends Model with Persistable with Description {

  import Isdiah._

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
  import play.api.data.Form
  import play.api.data.Forms._
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.RepositoryDescription),
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      AUTHORIZED_FORM_OF_NAME -> text,
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      ADDRESS_AREA -> list(Address.form.mapping),
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
        LANGUAGES_USED -> optional(list(nonEmptyText)),
        SCRIPTS_USED -> optional(list(nonEmptyText)),
        SOURCES -> optional(list(nonEmptyText)),
        MAINTENANCE_NOTES -> optional(text)
      )(IsdiahControl.apply)(IsdiahControl.unapply),
      ACCESS_POINTS -> list(AccessPoint.form.mapping),
      MAINTENANCE_EVENTS -> list(entity),
      UNKNOWN_DATA -> list(entity)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}

