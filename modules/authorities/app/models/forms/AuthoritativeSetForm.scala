package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{AuthoritativeSetF, Entity}
import defines.EntityType

/**
 * AuthoritativeSet model form.
 */
object AuthoritativeSetForm {

  import AuthoritativeSetF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.AuthoritativeSet),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(AuthoritativeSetF.apply)(AuthoritativeSetF.unapply)
  )
}
