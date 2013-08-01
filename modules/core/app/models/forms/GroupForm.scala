package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{GroupF, Entity}
import defines.EntityType

/**
 * Group model form.
 */
object GroupForm {

  import GroupF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.Group),
      Entity.ID -> optional(text),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      DESCRIPTION -> optional(nonEmptyText)
    )(GroupF.apply)(GroupF.unapply)
  )
}
