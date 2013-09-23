package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models._
import defines.EntityType
import models.IsadG._
import models.IsadGConditions
import models.IsadGContext
import models.IsadGContent
import models.IsadGMaterials
import models.IsadGControl
import models.IsadGConditions
import models.IsadGContext
import models.IsadGContent
import models.IsadGMaterials
import models.IsadGControl
import play.api.libs.json.Json

/**
 * IsadG description form.
 */
object IsadGForm {

  import IsadG._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.DocumentaryUnitDescription),
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      TITLE -> nonEmptyText,
      REF -> optional(text),
      ABSTRACT -> optional(nonEmptyText),
      DATES -> list(DatePeriodForm.form.mapping),
      LEVEL_OF_DESCRIPTION -> optional(text),
      EXTENT_MEDIUM -> optional(nonEmptyText),
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
        LANG_MATERIALS -> optional(list(nonEmptyText)),
        SCRIPT_MATERIALS -> optional(list(nonEmptyText)),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> optional(text)
      )(IsadGConditions.apply)(IsadGConditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> optional(text),
        LOCATION_COPIES -> optional(text),
        RELATED_UNITS -> optional(text),
        PUBLICATION_NOTE -> optional(text)
      )(IsadGMaterials.apply)(IsadGMaterials.unapply),
      NOTES -> optional(list(nonEmptyText)),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text)
      )(IsadGControl.apply)(IsadGControl.unapply),
      ACCESS_POINTS -> list(AccessPointForm.form.mapping),
      UNKNOWN_DATA -> list(entity)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}
