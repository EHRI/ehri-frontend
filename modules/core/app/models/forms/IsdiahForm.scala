package models.forms

import models._
import defines.EntityType

/**
 * Isdiah description form.
 */
object IsdiahForm {

  import play.api.data.Form
  import play.api.data.Forms._
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.RepositoryDescription),
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
      )(IsdiahDetails.apply)(IsdiahDetails.unapply),
      ACCESS_AREA -> mapping(
        OPENING_TIMES -> optional(text),
        CONDITIONS -> optional(text),
        ACCESSIBILITY -> optional(text)
      )(IsdiahAccess.apply)(IsdiahAccess.unapply),
      SERVICES_AREA -> mapping(
        RESEARCH_SERVICES -> optional(text),
        REPROD_SERVICES -> optional(text),
        PUBLIC_AREAS -> optional(text)
      )(IsdiahServices.apply)(IsdiahServices.unapply),
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
      )(IsdiahControl.apply)(IsdiahControl.unapply),
      ACCESS_POINTS -> list(AccessPointForm.form.mapping),
      MAINTENANCE_EVENTS -> list(entity),
      UNKNOWN_DATA -> list(entity)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}
