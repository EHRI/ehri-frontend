package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{DocumentaryUnitDescriptionF, Entity, IsadG}
import models.DocumentaryUnitDescriptionF._
import models.DocumentaryUnitDescriptionF.Materials
import models.DocumentaryUnitDescriptionF.Context
import models.DocumentaryUnitDescriptionF.Content
import models.DocumentaryUnitDescriptionF.Conditions

/**
 * IsadG description form.
 */
object IsadGForm {

  import IsadG._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      TITLE -> optional(nonEmptyText),
      DATES -> list(DatePeriodForm.form.mapping),
      LEVEL_OF_DESCRIPTION -> optional(enum(LevelOfDescription)),
      EXTENT_MEDIUM -> optional(nonEmptyText),
      CONTEXT_AREA -> mapping(
        ADMIN_BIOG -> optional(text),
        ARCH_HIST -> optional(text),
        ACQUISITION -> optional(text)
      )(Context.apply)(Context.unapply),
      CONTENT_AREA -> mapping(
        SCOPE_CONTENT -> optional(text),
        APPRAISAL -> optional(text),
        ACCRUALS -> optional(text),
        SYS_ARR -> optional(text)
      )(Content.apply)(Content.unapply),
      CONDITIONS_AREA -> mapping(
        ACCESS_COND -> optional(text),
        REPROD_COND -> optional(text),
        LANG_MATERIALS -> optional(list(nonEmptyText)),
        SCRIPT_MATERIALS -> optional(list(nonEmptyText)),
        PHYSICAL_CHARS -> optional(text),
        FINDING_AIDS -> optional(text)
      )(Conditions.apply)(Conditions.unapply),
      MATERIALS_AREA -> mapping(
        LOCATION_ORIGINALS -> optional(text),
        LOCATION_COPIES -> optional(text),
        RELATED_UNITS -> optional(text),
        PUBLICATION_NOTE -> optional(text)
      )(Materials.apply)(Materials.unapply),
      CONTROL_AREA -> mapping(
        ARCHIVIST_NOTE -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        DATES_DESCRIPTIONS -> optional(text)
      )(Control.apply)(Control.unapply)
    )(DocumentaryUnitDescriptionF.apply)(DocumentaryUnitDescriptionF.unapply)
  )
}
