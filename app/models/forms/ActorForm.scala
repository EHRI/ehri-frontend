package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{ActorF, Entity}
import models.base.DescribedEntity

/**
 * Actor model form.
 */
object ActorForm {

  import ActorF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(IsaarForm.form.mapping)
    )(ActorF.apply)(ActorF.unapply)
  )
}
