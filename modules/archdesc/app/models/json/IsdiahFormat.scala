package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._
import models.base.Description


object IsdiahFormat {

  import AddressFormat._
  import Entity._
  import Isdiah._
  import AccessPointFormat._
  import eu.ehri.project.definitions.Ontology._

  implicit val isdiahWrites = new Writes[RepositoryDescriptionF] {
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
          MAINTENANCE_NOTES -> d.control.maintenanceNotes
        ),
        RELATIONSHIPS -> Json.obj(
          ENTITY_HAS_ADDRESS -> Json.toJson(d.addresses.map(Json.toJson(_)).toSeq),
          HAS_ACCESS_POINT -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq),
          HAS_UNKNOWN_PROPERTY -> Json.toJson(d.unknownProperties.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  import RepositoryDescriptionF._

  implicit val isdiahReads: Reads[RepositoryDescriptionF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.RepositoryDescription)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANG_CODE).read[String] and
      (__ \ DATA \ AUTHORIZED_FORM_OF_NAME).read[String] and
      ((__ \ DATA \ OTHER_FORMS_OF_NAME).readNullable[List[String]] orElse
        (__ \ DATA \ OTHER_FORMS_OF_NAME).readNullable[String].map(os => os.map(List(_))) ) and
      ((__ \ DATA \ PARALLEL_FORMS_OF_NAME).readNullable[List[String]] orElse
        (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readNullable[String].map(os => os.map(List(_))) ) and
      (__ \ RELATIONSHIPS \ ENTITY_HAS_ADDRESS).lazyReadNullable[List[AddressF]](
          Reads.list[AddressF]).map(_.getOrElse(List.empty[AddressF])) and
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
          (__ \ LANGUAGES_USED).readNullable[List[String]] and
          (__ \ SCRIPTS_USED).readNullable[List[String]] and
          ((__ \ SOURCES).readNullable[List[String]] orElse
            (__ \ SOURCES).readNullable[String].map(os => os.map(List(_))) ) and
          (__ \ MAINTENANCE_NOTES).readNullable[String]
        )(IsdiahControl.apply _)) and
        (__ \ RELATIONSHIPS \ HAS_ACCESS_POINT)
            .lazyReadNullable(Reads.list[AccessPointF]).map(_.getOrElse(List.empty[AccessPointF])) and
        (__ \ RELATIONSHIPS \ HAS_UNKNOWN_PROPERTY)
            .lazyReadNullable(Reads.list[Entity]).map(_.getOrElse(List.empty[Entity]))
    )(RepositoryDescriptionF.apply _)

  implicit val restFormat: Format[RepositoryDescriptionF] = Format(isdiahReads, isdiahWrites)
}