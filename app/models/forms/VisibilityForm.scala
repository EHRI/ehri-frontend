package models.forms

import play.api.data._
import play.api.data.Forms._

import defines.EntityType
import rest.{RestPageParams, EntityDAO}

object VisibilityForm {

  val form = Form(single(
    RestPageParams.ACCESSOR_PARAM -> list(nonEmptyText)
  ))
}
