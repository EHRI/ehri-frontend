package forms

import play.api.data._
import play.api.data.Forms._


object VisibilityForm {

  val form = Form(single(
    backend.rest.Constants.ACCESSOR_PARAM -> seq(nonEmptyText)
  ))
}
