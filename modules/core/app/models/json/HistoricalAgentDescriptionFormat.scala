package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Described,Description}


object HistoricalAgentDescriptionFormat {
  import Entity._
  import HistoricalAgentF._
  import AccessPointFormat._
  import Isaar._
  import DatePeriodFormat._
  import eu.ehri.project.definitions.Ontology._

  implicit val HistoricalAgentTypeReads = defines.EnumUtils.enumReads(HistoricalAgentType)

  implicit val isaarWrites = new Writes[HistoricalAgentDescriptionF] {
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
          MAINTENANCE_NOTES -> d.control.maintenanceNotes
        ),
        RELATIONSHIPS -> Json.obj(
          HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq),
          ENTITY_HAS_DATE -> Json.toJson(d.dates.map(Json.toJson(_)))
        )
      )
    }
  }

  implicit val isaarReads: Reads[HistoricalAgentDescriptionF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.HistoricalAgentDescription)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANG_CODE).read[String] and
      ((__ \ DATA \ ENTITY_TYPE).read[HistoricalAgentType.Value]
          orElse Reads.pure(HistoricalAgentType.CorporateBody)) and
      ((__ \ DATA \ AUTHORIZED_FORM_OF_NAME).read[String]
          orElse Reads.pure(UNNAMED_PLACEHOLDER)) and
      ((__ \ DATA \ OTHER_FORMS_OF_NAME).readNullable[List[String]] orElse
        (__ \ DATA \ OTHER_FORMS_OF_NAME).readNullable[String].map(os => os.map(List(_))) ) and
      ((__ \ DATA \ PARALLEL_FORMS_OF_NAME).readNullable[List[String]] orElse
        (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readNullable[String].map(os => os.map(List(_))) ) and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_DATE).lazyReadNullable[List[DatePeriodF]](
        Reads.list[DatePeriodF]).map(_.toList.flatten) and
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
        ((__ \ SOURCES).readNullable[List[String]] orElse
          (__ \ SOURCES).readNullable[String].map(os => os.map(List(_))) ) and
        (__ \ MAINTENANCE_NOTES).readNullable[String]
      )(IsaarControl.apply _)) and
      (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT).lazyReadNullable(
          Reads.list[AccessPointF]).map(_.getOrElse(List.empty[AccessPointF])) and
      (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY)
        .lazyReadNullable(Reads.list[Entity]).map(_.getOrElse(List.empty[Entity]))
  )(HistoricalAgentDescriptionF.apply _)

  implicit val restFormat: Format[HistoricalAgentDescriptionF] = Format(isaarReads,isaarWrites)
}