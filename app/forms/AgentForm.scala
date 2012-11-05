package forms

import play.api.data._
import play.api.data.Forms._

import models._

object AgentForm {

  val form = Form(
      mapping(
    		"id" -> optional(longNumber),
    		"identifier" -> nonEmptyText,
    		"name" -> nonEmptyText
      )(Agent.apply)(Agent.unform)
  ) 
}
