package models.forms

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.Forms._
import models.{RepositoryDescriptionF, AddressF, Isdiah, Entity}
import models.RepositoryDescriptionF.{Control, Services, Access, Details}

/**
 * Isdiah description form.
 */
object IsdiahForm {

  import Isdiah._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      LANG_CODE -> nonEmptyText,
      AUTHORIZED_FORM_OF_NAME -> text,
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      ADDRESS_AREA -> list(AddressForm.form.mapping),
      DESCRIPTION_AREA -> mapping(
        HISTORY -> optional(nonEmptyText),
        GEOCULTURAL_CONTEXT -> optional(nonEmptyText),
        MANDATES -> optional(nonEmptyText),
        ADMINISTRATIVE_STRUCTURE -> optional(nonEmptyText),
        RECORDS -> optional(nonEmptyText),
        BUILDINGS -> optional(nonEmptyText),
        HOLDINGS -> optional(nonEmptyText),
        FINDING_AIDS -> optional(nonEmptyText)
      )(Details.apply)(Details.unapply),
      ACCESS_AREA -> mapping(
        OPENING_TIMES -> optional(text),
        CONDITIONS -> optional(text),
        ACCESSIBILITY -> optional(text)
      )(Access.apply)(Access.unapply),
      SERVICES_AREA -> mapping(
        RESEARCH_SERVICES -> optional(text),
        REPROD_SERVICES -> optional(text),
        PUBLIC_AREAS -> optional(text)
      )(Services.apply)(Services.unapply),
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
      )(Control.apply)(Control.unapply)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}
