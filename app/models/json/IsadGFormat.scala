package models.json

import play.api.libs.json._
import models._
import models.base.TemporalEntity
import play.api.libs.functional.syntax._
import defines.EnumReader._


object IsadGFormat {
  import defines.EnumWriter.enumWrites
  import Entity._
  import IsadG._
  import DatePeriodFormat._

  implicit val isadGWrites = new Writes[DocumentaryUnitDescriptionF] {
    def writes(d: DocumentaryUnitDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TITLE -> d.title,
          LANG_CODE -> d.languageCode,
          LEVEL_OF_DESCRIPTION -> d.levelOfDescription,
          EXTENT_MEDIUM -> d.extentAndMedium,
          ADMIN_BIOG -> d.context.adminBiogHistory,
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
          ARCHIVIST_NOTE -> d.control.archivistNote,
          RULES_CONVENTIONS -> d.control.rulesAndConventions,
          DATES_DESCRIPTIONS -> d.control.datesOfDescriptions
        ),
        RELATIONSHIPS -> Json.obj(
          TemporalEntity.DATE_REL -> Json.toJson(d.dates.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  import DocumentaryUnitDescriptionF._

  implicit val levelOfDescriptionReads = enumReads(LevelOfDescription)

  implicit val isadGReads: Reads[DocumentaryUnitDescriptionF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANG_CODE).read[String] and
      (__ \ DATA \ TITLE).readNullable[String] and
      (__ \ RELATIONSHIPS \ TemporalEntity.DATE_REL).lazyRead[List[DatePeriodF]](
        Reads.list[DatePeriodF]) and
      (__ \ DATA \ LEVEL_OF_DESCRIPTION).readNullable[LevelOfDescription.Value] and
      (__ \ DATA \ EXTENT_MEDIUM).readNullable[String] and
      (__ \ DATA).read[Context]((
        (__ \ ADMIN_BIOG).readNullable[String] and
          (__ \ ARCH_HIST).readNullable[String] and
          (__ \ ACQUISITION).readNullable[String]
        )(Context.apply _)) and
      (__ \ DATA).read[Content]((
        (__ \ SCOPE_CONTENT).readNullable[String] and
          (__ \ APPRAISAL).readNullable[String] and
          (__ \ ACCRUALS).readNullable[String] and
          (__ \ SYS_ARR).readNullable[String]
        )(Content.apply _)) and
      (__ \ DATA).read[Conditions]((
        (__ \ ACCESS_COND).readNullable[String] and
          (__ \ REPROD_COND).readNullable[String] and
          (__ \ LANG_MATERIALS).readNullable[List[String]] and
          (__ \ SCRIPT_MATERIALS).readNullable[List[String]] and
          (__ \ PHYSICAL_CHARS).readNullable[String] and
          (__ \ FINDING_AIDS).readNullable[String]
        )(Conditions.apply _)) and
      (__ \ DATA).read[Materials]((
        (__ \ LOCATION_ORIGINALS).readNullable[String] and
          (__ \ LOCATION_COPIES).readNullable[String] and
          (__ \ RELATED_UNITS).readNullable[String] and
          (__ \ PUBLICATION_NOTE).readNullable[String]
        )(Materials.apply _)) and
      (__ \ DATA).read[Control]((
        (__ \ ARCHIVIST_NOTE).readNullable[String] and
          (__ \ RULES_CONVENTIONS).readNullable[String] and
          (__ \ DATES_DESCRIPTIONS).readNullable[String]
        )(Control.apply _))
  )(DocumentaryUnitDescriptionF.apply _)

  implicit val isadGFormat: Format[DocumentaryUnitDescriptionF] = Format(isadGReads,isadGWrites)
}