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
      AUTHORIZED_FORM_OF_NAME -> optional(text),
      OTHER_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      PARALLEL_FORMS_OF_NAME -> optional(list(nonEmptyText)),
      ADDRESS_AREA -> list(
        mapping(
          Entity.ID -> optional(nonEmptyText),
          ADDRESS_NAME -> nonEmptyText,
          CONTACT_PERSON -> optional(nonEmptyText),
          STREET_ADDRESS -> optional(nonEmptyText),
          CITY -> optional(nonEmptyText),
          REGION -> optional(nonEmptyText),
          POSTAL_CODE -> optional(nonEmptyText),
          COUNTRY_CODE -> optional(nonEmptyText),
          EMAIL -> optional(email),
          TELEPHONE -> optional(list(nonEmptyText)),
          FAX -> optional(nonEmptyText),
          URL -> optional(nonEmptyText)
        )(AddressF.apply)(AddressF.unapply)
      ),
      DESCRIPTION_AREA -> mapping(
        HISTORY -> optional(text),
        GENERAL_CONTEXT -> optional(text),
        MANDATES -> optional(text),
        ADMINISTRATIVE_STRUCTURE -> optional(text),
        RECORDS -> optional(text),
        BUILDINGS -> optional(text),
        HOLDINGS -> optional(text),
        FINDING_AIDS -> optional(text)
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
        SOURCES -> optional(text),
        MAINTENANCE_NOTES -> optional(text)
      )(Control.apply)(Control.unapply)
    )(RepositoryDescriptionF.apply)(RepositoryDescriptionF.unapply)
  )
}
