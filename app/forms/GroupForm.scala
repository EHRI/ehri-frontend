package forms

import play.api.data._
import play.api.data.Forms._

import models._

object GroupForm {

  val form = Form(
      mapping(
    		"id" -> optional(longNumber),
    		"identifier" -> nonEmptyText,
    		"name" -> nonEmptyText
      )(Group.apply)(Group.unform)
  ) 
}
