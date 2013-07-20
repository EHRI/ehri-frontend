package forms

import play.api.data._
import play.api.data.Forms._

import rest.RestPageParams

object VisibilityForm {

  val form = Form(single(
    RestPageParams.ACCESSOR_PARAM -> list(nonEmptyText)
  ))
}
