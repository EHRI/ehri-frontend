package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{Isaar, HistoricalAgentDescriptionF, Entity}
import models.HistoricalAgentDescriptionF.{Control, Details}

/**
 * ISAAR description form.
 */
object IsaarForm {

  import Isaar._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      ENTITY_TYPE -> enum(ActorType),
      AUTHORIZED_FORM_OF_NAME -> nonEmptyText,
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      DESCRIPTION_AREA -> mapping(
        DATES_OF_EXISTENCE -> optional(text),
        HISTORY -> optional(text),
        PLACES -> optional(text),
        LEGAL_STATUS -> optional(text),
        FUNCTIONS -> optional(text),
        MANDATES -> optional(text),
        INTERNAL_STRUCTURE -> optional(text),
        GENERAL_CONTEXT -> optional(text)
      )(Details.apply)(Details.unapply),
      CONTROL_AREA -> mapping(
        DESCRIPTION_IDENTIFIER -> optional(text),
        INSTITUTION_IDENTIFIER -> optional(text),
        RULES_CONVENTIONS -> optional(text),
        STATUS -> optional(text),
        LEVEL_OF_DETAIL -> optional(text),
        DATES_CVD -> optional(text),
        LANGUAGES_USED -> optional(list(nonEmptyText)),
        SCRIPTS_USED -> optional(list(nonEmptyText)),
        SOURCES -> optional(text),
        MAINTENANCE_NOTES -> optional(text)
      )(Control.apply)(Control.unapply)
    )(HistoricalAgentDescriptionF.apply)(HistoricalAgentDescriptionF.unapply)
  )
}
