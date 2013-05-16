package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{AuthoritativeSetF, Entity}

/**
 * AuthoritativeSet model form.
 */
object AuthoritativeSetForm {

  import AuthoritativeSetF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(AuthoritativeSetF.apply)(AuthoritativeSetF.unapply)
  )
}
