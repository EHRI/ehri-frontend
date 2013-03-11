package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{ConceptF, Entity}


/**
 * Concept model form.
 */
object ConceptForm {
  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      "descriptions" -> list(ConceptDescriptionForm.form.mapping)
    )(ConceptF.apply)(ConceptF.unapply)
  )
}
