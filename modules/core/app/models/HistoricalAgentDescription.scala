package models

import models.base._
import defines.EntityType
import models.json.{ClientConvertable, RestConvertable}
import play.api.libs.json.Json
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import models.forms._

case class IsaarDetail(
  datesOfExistence: Option[String] = None,
  history: Option[String] = None,
  places: Option[String] = None,
  legalStatus: Option[String] = None,
  functions: Option[String] = None,
  mandates: Option[String] = None,
  internalStructure: Option[String] = None,
  generalContext: Option[String] = None
) extends AttributeSet

case class IsaarControl(
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



object HistoricalAgentDescriptionF {

  lazy implicit val historicalAgentDescriptionFormat = json.HistoricalAgentDescriptionFormat.restFormat

  implicit object Converter extends RestConvertable[HistoricalAgentDescriptionF] with ClientConvertable[HistoricalAgentDescriptionF] {
    val restFormat = models.json.HistoricalAgentDescriptionFormat.restFormat

    private implicit val entityFormat = json.entityFormat
    private implicit val accessPointFormat = AccessPointF.Converter.clientFormat
    private implicit val datePeriodFormat = DatePeriodF.Converter.clientFormat
    private implicit val isaarDetailsFormat = Json.format[IsaarDetail]
    private implicit val isaarControlFormat = Json.format[IsaarControl]
    val clientFormat = Json.format[HistoricalAgentDescriptionF]
  }
}

case class HistoricalAgentDescriptionF(
  isA: EntityType.Value = EntityType.HistoricalAgentDescription,
  id: Option[String],
  languageCode: String,
  entityType: Isaar.HistoricalAgentType.Value,
  name: String,
  otherFormsOfName: Option[List[String]] = None,
  parallelFormsOfName: Option[List[String]] = None,

  @Annotations.Relation(Ontology.ENTITY_HAS_DATE)
  dates: List[DatePeriodF] = Nil,
  details: IsaarDetail,
  control: IsaarControl,
  accessPoints: List[AccessPointF],
  unknownProperties: List[Entity] = Nil
) extends Model
  with Persistable
  with Description
  with Temporal {

  import Isaar._

  def toSeq = Seq(
    DATES_OF_EXISTENCE -> details.datesOfExistence,
    HISTORY -> details.history,
    PLACES -> details.places,
    LEGAL_STATUS -> details.legalStatus,
    FUNCTIONS -> details.functions,
    MANDATES -> details.mandates,
    INTERNAL_STRUCTURE -> details.internalStructure,
    GENERAL_CONTEXT -> details.generalContext,
    DESCRIPTION_IDENTIFIER -> control.descriptionIdentifier,
    INSTITUTION_IDENTIFIER -> control.institutionIdentifier,
    RULES_CONVENTIONS -> control.rulesAndConventions,
    STATUS -> control.status,
    LEVEL_OF_DETAIL -> control.levelOfDetail,
    DATES_CVD -> control.datesCDR,
    MAINTENANCE_NOTES -> control.maintenanceNotes
  )
}

object HistoricalAgentDescription {
  import Isaar._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.HistoricalAgentDescription),
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      ENTITY_TYPE -> enum(HistoricalAgentType),
      AUTHORIZED_FORM_OF_NAME -> nonEmptyText,
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      DATES -> list(DatePeriod.form.mapping),
      DESCRIPTION_AREA -> mapping(
        DATES_OF_EXISTENCE -> optional(text),
        HISTORY -> optional(text),
        PLACES -> optional(text),
        LEGAL_STATUS -> optional(text),
        FUNCTIONS -> optional(text),
        MANDATES -> optional(text),
        INTERNAL_STRUCTURE -> optional(text),
        GENERAL_CONTEXT -> optional(text)
      )(IsaarDetail.apply)(IsaarDetail.unapply),
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
      )(IsaarControl.apply)(IsaarControl.unapply),
      ACCESS_POINTS -> list(AccessPoint.form.mapping),
      UNKNOWN_DATA -> list(entity)
    )(HistoricalAgentDescriptionF.apply)(HistoricalAgentDescriptionF.unapply)
  )
}