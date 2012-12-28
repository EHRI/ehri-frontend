package models.forms

import play.api.data._
import play.api.data.Forms._

object UserForm {

  val openid = Form(single(
    "openid_identifier" -> nonEmptyText
  )) 
}
