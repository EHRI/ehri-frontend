package forms

import play.api.data._
import play.api.data.Forms._
import models._
import models.base.Publishable
import models.base.AccessibleEntity

object AgentForm {

  import Agent._
  import Address._
  import AgentDescription._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME.id -> nonEmptyText,
      Publishable.PUBLICATION_STATUS.id -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(
        mapping(
          Entity.ID -> optional(nonEmptyText),
          "languageCode" -> nonEmptyText,
          NAME.id -> optional(text),
          OTHER_FORMS_OF_NAME.id -> optional(list(text)),
          PARALLEL_FORMS_OF_NAME.id -> optional(list(text)),
          "addresses" -> list(
            mapping(
              Entity.ID -> optional(nonEmptyText),
              ADDRESS_NAME.id -> nonEmptyText,
              CONTACT_PERSON.id -> optional(text),
              STREET_ADDRESS.id -> optional(text),
              CITY.id -> optional(text),
              REGION.id -> optional(text),
              COUNTRY_CODE.id -> optional(text),
              EMAIL.id -> optional(email),
              TELEPHONE.id -> optional(text),
              FAX.id -> optional(text),
              URL.id -> optional(text)
            )(Address.apply)(Address.unapply)
          ),
          "description" -> mapping(
            HISTORY.id -> optional(text),
            GENERAL_CONTEXT.id -> optional(text),
            MANDATES.id -> optional(text),
            ADMINISTRATIVE_STRUCTURE.id -> optional(text),
            RECORDS.id -> optional(text),
            BUILDINGS.id -> optional(text),
            HOLDINGS.id -> optional(text),
            FINDING_AIDS.id -> optional(text)
          )(Details.apply)(Details.unapply),
          "access" -> mapping(
            OPENING_TIMES.id -> optional(text),
            CONDITIONS.id -> optional(text),
            ACCESSIBILITY.id -> optional(text)
          )(Access.apply)(Access.unapply),
          "services" -> mapping(
            RESEARCH_SERVICES.id -> optional(text),
            REPROD_SERVICES.id -> optional(text),
            PUBLIC_AREAS.id -> optional(text)
          )(Services.apply)(Services.unapply),
          "control" -> mapping(
            DESCRIPTION_IDENTIFIER.id -> optional(text),
            INSTITUTION_IDENTIFIER.id -> optional(text),
            RULES_CONVENTIONS.id -> optional(text),
            STATUS.id -> optional(text),
            LEVEL_OF_DETAIL.id -> optional(text),
            DATES_CVD.id -> optional(text),
            LANGUAGES_USED.id -> optional(list(text)),
            SCRIPTS_USED.id -> optional(list(text)),
            SOURCES.id -> optional(text),
            MAINTENANCE_NOTES.id -> optional(text)
          )(Control.apply)(Control.unapply)
        )(AgentDescription.apply)(AgentDescription.unapply)
      )
    )(Agent.apply)(Agent.unapply)
  )
}
