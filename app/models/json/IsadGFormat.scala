package models.json

import play.api.libs.json._
import models._
import models.base.Description
import play.api.libs.functional.syntax._
import defines.EntityType
import defines.EnumUtils._


object IsadGFormat {
  import Entity._
  import IsadG._
  import DatePeriodFormat._
  import AccessPointFormat._

  implicit val isadGWrites = new Writes[DocumentaryUnitDescriptionF] {
    def writes(d: DocumentaryUnitDescriptionF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          TITLE -> d.name,
          ABSTRACT -> d.`abstract`,
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
          NOTES -> d.notes,
          ARCHIVIST_NOTE -> d.control.archivistNote,
          RULES_CONVENTIONS -> d.control.rulesAndConventions,
          DATES_DESCRIPTIONS -> d.control.datesOfDescriptions
        ),
        RELATIONSHIPS -> Json.obj(
          DatePeriodF.DATE_REL -> Json.toJson(d.dates.map(Json.toJson(_)).toSeq),
          Description.ACCESS_REL -> Json.toJson(d.accessPoints.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  import DocumentaryUnitDescriptionF._

  private implicit val levelOfDescriptionReads = enumReads(LevelOfDescription)
  implicit val datePeriodReads = DatePeriodFormat.restFormat

  implicit val isadGReads: Reads[DocumentaryUnitDescriptionF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.DocumentaryUnitDescription)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ LANG_CODE).read[String] and
      (__ \ DATA \ TITLE).read[String] and
      (__ \ DATA \ ABSTRACT).readNullable[String] and
      ((__ \ RELATIONSHIPS \ DatePeriodF.DATE_REL).lazyRead[List[DatePeriodF]](
        Reads.list[DatePeriodF](datePeriodReads)) orElse Reads.pure(Nil)) and
      (__ \ DATA \ LEVEL_OF_DESCRIPTION).readNullable[String] and
      (__ \ DATA \ EXTENT_MEDIUM).readNullable[String] and
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
          ((__ \ LANG_MATERIALS).readNullable[List[String]] orElse
            (__ \ LANG_MATERIALS).readNullable[String].map(os => os.map(List(_))) ) and
          ((__ \ SCRIPT_MATERIALS).readNullable[List[String]] orElse
            (__ \ SCRIPT_MATERIALS).readNullable[String].map(os => os.map(List(_))) ) and
          (__ \ PHYSICAL_CHARS).readNullable[String] and
          (__ \ FINDING_AIDS).readNullable[String]
        )(IsadGConditions.apply _)) and
      (__ \ DATA).read[IsadGMaterials]((
        (__ \ LOCATION_ORIGINALS).readNullable[String] and
          (__ \ LOCATION_COPIES).readNullable[String] and
          (__ \ RELATED_UNITS).readNullable[String] and
          (__ \ PUBLICATION_NOTE).readNullable[String]
        )(IsadGMaterials.apply _)) and
      ((__ \ NOTES).readNullable[List[String]] orElse
        (__ \ NOTES).readNullable[String].map(os => os.map(List(_))) ) and
      (__ \ DATA).read[IsadGControl]((
        (__ \ ARCHIVIST_NOTE).readNullable[String] and
          (__ \ RULES_CONVENTIONS).readNullable[String] and
          (__ \ DATES_DESCRIPTIONS).readNullable[String]
        )(IsadGControl.apply _)) and
      ((__ \ RELATIONSHIPS \ AccessPointF.RELATES_REL).lazyRead[List[AccessPointF]](
        Reads.list[AccessPointF]) orElse Reads.pure(Nil))
  )(DocumentaryUnitDescriptionF.apply _)

  implicit val restFormat: Format[DocumentaryUnitDescriptionF] = Format(isadGReads,isadGWrites)
}
