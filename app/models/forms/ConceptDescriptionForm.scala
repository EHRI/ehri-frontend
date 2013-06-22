package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{ConceptF, ConceptDescriptionF, Entity}
import defines.EntityType

/**
 * Concept description form.
 */
object ConceptDescriptionForm {

  import ConceptF._

  val form = Form(mapping(
    Entity.ISA -> ignored(EntityType.ConceptDescription),
    Entity.ID -> optional(nonEmptyText),
    LANGUAGE -> nonEmptyText,
    PREFLABEL -> nonEmptyText,
    ALTLABEL -> optional(list(nonEmptyText)),
    DEFINITION -> optional(list(nonEmptyText)),
    SCOPENOTE -> optional(list(nonEmptyText))
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}
