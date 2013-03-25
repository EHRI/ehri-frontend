package models.json

import play.api.libs.json._
import models._
import models.base.TemporalEntity
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._


object IsdiahFormat {

  import AddressFormat._
  import Entity._
  import Isdiah._

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
          GENERAL_CONTEXT -> d.details.generalContext,
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
          RepositoryF.ADDRESS_REL -> Json.toJson(d.addresses.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  import RepositoryDescriptionF._

  implicit val isdiahReads: Reads[RepositoryDescriptionF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.RepositoryDescription)) andKeep
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANG_CODE).read[String] and
      (__ \ DATA \ AUTHORIZED_FORM_OF_NAME).readNullable[String] and
      (__ \ DATA \ OTHER_FORMS_OF_NAME).readNullable[List[String]] and
      (__ \ DATA \ PARALLEL_FORMS_OF_NAME).readNullable[List[String]] and
      ((__ \ RELATIONSHIPS \ RepositoryF.ADDRESS_REL).lazyRead[List[AddressF]](Reads.list[AddressF])
          orElse Reads.pure(Nil)) and
      (__ \ DATA).read[Details]((
        (__ \ HISTORY).readNullable[String] and
          (__ \ GENERAL_CONTEXT).readNullable[String] and
          (__ \ MANDATES).readNullable[String] and
          (__ \ ADMINISTRATIVE_STRUCTURE).readNullable[String] and
          (__ \ RECORDS).readNullable[String] and
          (__ \ BUILDINGS).readNullable[String] and
          (__ \ HOLDINGS).readNullable[String] and
          (__ \ FINDING_AIDS).readNullable[String]
        )(Details.apply _)) and
      (__ \ DATA).read[Access]((
        (__ \ OPENING_TIMES).readNullable[String] and
          (__ \ CONDITIONS).readNullable[String] and
          (__ \ ACCESSIBILITY).readNullable[String]
        )(Access.apply _)) and
      (__ \ DATA).read[Services]((
        (__ \ RESEARCH_SERVICES).readNullable[String] and
          (__ \ REPROD_SERVICES).readNullable[String] and
          (__ \ PUBLIC_AREAS).readNullable[String]
        )(Services.apply _)) and
      (__ \ DATA).read[Control]((
        (__ \ DESCRIPTION_IDENTIFIER).readNullable[String] and
          (__ \ INSTITUTION_IDENTIFIER).readNullable[String] and
          (__ \ RULES_CONVENTIONS).readNullable[String] and
          (__ \ STATUS).readNullable[String] and
          (__ \ LEVEL_OF_DETAIL).readNullable[String] and
          (__ \ DATES_CVD).readNullable[String] and
          (__ \ LANGUAGES_USED).readNullable[List[String]] and
          (__ \ SCRIPTS_USED).readNullable[List[String]] and
          (__ \ SOURCES).readNullable[String] and
          (__ \ MAINTENANCE_NOTES).readNullable[String]
        )(Control.apply _))
    )(RepositoryDescriptionF.apply _)

  implicit val isdiahFormat: Format[RepositoryDescriptionF] = Format(isdiahReads, isdiahWrites)
}