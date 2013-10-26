package forms

import play.api.data._
import play.api.data.Forms._

import utils.PageParams
import utils.PageParams

object VisibilityForm {

  val form = Form(single(
    rest.Constants.ACCESSOR_PARAM -> list(nonEmptyText)
  ))
}
