package forms

import play.api.data._
import play.api.data.Forms._

import models._

object UserProfileForm {

  val form = Form(
      mapping(
    		"id" -> optional(longNumber),
    		"identifier" -> nonEmptyText,
    		"name" -> nonEmptyText
      )(UserProfile.apply)(UserProfile.unform)
  ) 
}
