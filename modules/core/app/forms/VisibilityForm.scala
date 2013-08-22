package forms

import play.api.data._
import play.api.data.Forms._

import utils.ListParams
import utils.ListParams

object VisibilityForm {

  val form = Form(single(
    rest.Constants.ACCESSOR_PARAM -> list(nonEmptyText)
  ))
}
