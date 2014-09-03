package models

import models.base._
import defines.EntityType
import models.json._
import play.api.libs.functional.syntax._
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import models.forms._
import backend.{BackendReadable, BackendWriteable}
import Description._


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

  import Entity._
  import HistoricalAgentF._
  import Isaar._
  import eu.ehri.project.definitions.Ontology._

  implicit val historicalAgentDescriptionWrites = new Writes[HistoricalAgentDescriptionF] {
    def writes(d: HistoricalAgentDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          AUTHORIZED_FORM_OF_NAME -> d.name,
          ENTITY_TYPE -> d.entityType,
          LANG_CODE -> d.languageCode,
          OTHER_FORMS_OF_NAME -> d.otherFormsOfName,
          PARALLEL_FORMS_OF_NAME -> d.parallelFormsOfName,
          DATES_OF_EXISTENCE -> d.details.datesOfExistence,
          HISTORY -> d.details.history,
          PLACES -> d.details.places,
          LEGAL_STATUS -> d.details.legalStatus,
          FUNCTIONS -> d.details.functions,
          MANDATES -> d.details.mandates,
          INTERNAL_STRUCTURE -> d.details.internalStructure,
          GENERAL_CONTEXT -> d.details.generalContext,
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
          HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq),
          ENTITY_HAS_DATE -> Json.toJson(d.dates.map(Json.toJson(_)))
        )
      )
    }
  }

  implicit val historicalAgentDescriptionReads: Reads[HistoricalAgentDescriptionF] = (
    (__ \ TYPE).readIfEquals(EntityType.HistoricalAgentDescription) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ LANG_CODE).read[String] and
    (__ \ DATA \ ENTITY_TYPE).readWithDefault(HistoricalAgentType.CorporateBody) and
    (__ \ DATA \ AUTHORIZED_FORM_OF_NAME).readWithDefault(UNNAMED_PLACEHOLDER) and
    (__ \ DATA \ OTHER_FORMS_OF_NAME).readListOrSingleNullable[String] and
    (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readListOrSingleNullable[String] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).nullableListReads[DatePeriodF] and
    (__ \ DATA).read[IsaarDetail]((
      (__ \ DATES_OF_EXISTENCE).readNullable[String] and
      (__ \ HISTORY).readNullable[String] and
      (__ \ PLACES).readNullable[String] and
      (__ \ LEGAL_STATUS).readNullable[String] and
      (__ \ FUNCTIONS).readNullable[String] and
      (__ \ MANDATES).readNullable[String] and
      (__ \ INTERNAL_STRUCTURE).readNullable[String] and
      (__ \ GENERAL_CONTEXT).readNullable[String]
    )(IsaarDetail.apply _)) and
    (__ \ DATA).read[IsaarControl]((
      (__ \ DESCRIPTION_IDENTIFIER).readNullable[String] and
      (__ \ INSTITUTION_IDENTIFIER).readNullable[String] and
      (__ \ RULES_CONVENTIONS).readNullable[String] and
      (__ \ STATUS).readNullable[String] and
      (__ \ LEVEL_OF_DETAIL).readNullable[String] and
      (__ \ DATES_CVD).readNullable[String] and
      (__ \ LANGUAGES_USED).readNullable[List[String]] and
      (__ \ SCRIPTS_USED).readNullable[List[String]] and
      (__ \ SOURCES).readListOrSingleNullable[String] and
      (__ \ MAINTENANCE_NOTES).readNullable[String]
    )(IsaarControl.apply _)) and
    (__ \ DATA \ CREATION_PROCESS).readWithDefault(CreationProcess.Manual) and
    (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).nullableListReads[AccessPointF] and
    (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY).nullableListReads[Entity]
  )(HistoricalAgentDescriptionF.apply _)

  implicit object Converter extends BackendReadable[HistoricalAgentDescriptionF] with BackendWriteable[HistoricalAgentDescriptionF]  {
    val restReads = historicalAgentDescriptionReads
    val restFormat = Format(historicalAgentDescriptionReads,historicalAgentDescriptionWrites)
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
  creationProcess: CreationProcess.Value = CreationProcess.Manual,
  accessPoints: List[AccessPointF],
  unknownProperties: List[Entity] = Nil
) extends Model
  with Persistable
  with Description
  with Temporal {

  def displayText = details.history orElse details.generalContext orElse details.functions

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
  import Entity._

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.HistoricalAgentDescription),
      ID -> optional(nonEmptyText),
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
      CREATION_PROCESS -> default(enum(CreationProcess), CreationProcess.Manual),
      ACCESS_POINTS -> list(AccessPoint.form.mapping),
      UNKNOWN_DATA -> list(entity)
    )(HistoricalAgentDescriptionF.apply)(HistoricalAgentDescriptionF.unapply)
  )
}