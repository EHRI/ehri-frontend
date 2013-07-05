package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{ConceptF, ConceptDescriptionF, Entity}
import defines.EntityType
import play.api.libs.json.Json

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
    SCOPENOTE -> optional(list(nonEmptyText)),
    ACCESS_POINTS -> list(AccessPointForm.form.mapping),
    UNKNOWN_DATA -> list(entity)
  )(ConceptDescriptionF.apply)(ConceptDescriptionF.unapply))
}
