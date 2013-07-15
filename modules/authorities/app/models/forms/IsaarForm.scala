package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models._
import defines.EntityType
import play.api.libs.json.Json

/**
 * ISAAR description form.
 */
object IsaarForm {

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
      DATES -> list(DatePeriodForm.form.mapping),
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
      ACCESS_POINTS -> list(AccessPointForm.form.mapping),
      UNKNOWN_DATA -> list(entity)
    )(HistoricalAgentDescriptionF.apply)(HistoricalAgentDescriptionF.unapply)
  )
}
