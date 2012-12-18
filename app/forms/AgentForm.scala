package forms

import play.api.data._
import play.api.data.Forms._
import models._
import models.base.Publishable
import models.base.AccessibleEntity

object AgentForm {

  import Agent._
  import Address._
  
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
          )(AgentDetails.apply)(AgentDetails.unapply)
        )(AgentDescription.apply)(AgentDescription.unapply)
      )
    )(Agent.apply)(Agent.unapply)
  )
}
