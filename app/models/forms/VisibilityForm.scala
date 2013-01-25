package models.forms

import play.api.data._
import play.api.data.Forms._

import defines.EntityType

object VisibilityForm {

  val form = Form(single(
    s"accessor" -> list(nonEmptyText)
  ))
}
