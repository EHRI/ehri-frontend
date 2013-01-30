package models.forms

import play.api.data._
import play.api.data.Forms._

import defines.EntityType
import rest.EntityDAO

object VisibilityForm {

  val form = Form(single(
    EntityDAO.ACCESSOR_PARAM -> list(nonEmptyText)
  ))
}
