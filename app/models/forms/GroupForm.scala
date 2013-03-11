package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{GroupF, Entity}

/**
 * Group model form.
 */
object GroupForm {

  import GroupF._

  val form = Form(
    mapping(
      Entity.ID -> optional(text),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}
