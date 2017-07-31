package forms

import play.api.data._
import play.api.data.Forms._


object VisibilityForm {

  val form = Form(single(
    services.data.Constants.ACCESSOR_PARAM -> seq(nonEmptyText)
  ))
}
