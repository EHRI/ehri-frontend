package forms

import play.api.data._
import play.api.data.Forms._

import models._

object AgentForm {

  val form = Form(
    mapping(
      "id" -> optional(text),
      "identifier" -> nonEmptyText,
      Agent.NAME.id -> nonEmptyText,
      "publicationStatus" -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(
        mapping(
          "id" -> optional(text),
          "languageCode" -> nonEmptyText,
          Agent.NAME.id -> optional(text),
          Agent.OTHER_FORMS_OF_NAME.id -> list(text),
          Agent.PARALLEL_FORMS_OF_NAME.id -> list(text),
          "addresses" -> list(
            mapping(
              "id" -> optional(text),
              Address.ADDRESS_NAME.id -> nonEmptyText,
              Address.CONTACT_PERSON.id -> optional(text),
              Address.STREET_ADDRESS.id -> optional(text),
              Address.CITY.id -> optional(text),
              Address.REGION.id -> optional(text),
              Address.COUNTRY_CODE.id -> optional(text),
              Address.EMAIL.id -> optional(email),
              Address.TELEPHONE.id -> optional(text),
              Address.FAX.id -> optional(text),
              Address.URL.id -> optional(text)
            )(Address.apply)(Address.unapply)
          ),
          Agent.GENERAL_CONTEXT.id -> optional(text)
        )(AgentDescription.apply)(AgentDescription.unapply)
      )
    )(Agent.apply)(Agent.unapply)
  )
}
