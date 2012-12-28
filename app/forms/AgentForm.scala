package forms

import play.api.data._
import play.api.data.Forms._
import models._
import models.base.Publishable
import models.base.AccessibleEntity

object AgentDescriptionForm {
  import AgentDescription._
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      "languageCode" -> nonEmptyText,
      NAME -> optional(text),
      OTHER_FORMS_OF_NAME -> optional(list(text)),
      PARALLEL_FORMS_OF_NAME -> optional(list(text)),
      ADDRESS_AREA -> list(
        mapping(
          Entity.ID -> optional(nonEmptyText),
          ADDRESS_NAME -> nonEmptyText,
          CONTACT_PERSON -> optional(text),
          STREET_ADDRESS -> optional(text),
          CITY -> optional(text),
          REGION -> optional(text),
          COUNTRY_CODE -> optional(text),
          EMAIL -> optional(email),
          TELEPHONE -> optional(text),
          FAX -> optional(text),
          URL -> optional(text)
        )(Address.apply)(Address.unapply)
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
        LANGUAGES_USED -> optional(list(text)),
        SCRIPTS_USED -> optional(list(text)),
        SOURCES -> optional(text),
        MAINTENANCE_NOTES -> optional(text)
      )(Control.apply)(Control.unapply)
    )(AgentDescription.apply)(AgentDescription.unapply)
  )
}

object AgentForm {


  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      Isdiah.NAME -> nonEmptyText,
      Isdiah.PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(AgentDescriptionForm.form.mapping)
    )(Agent.apply)(Agent.unapply)
  )
}
