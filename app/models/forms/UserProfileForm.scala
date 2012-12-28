package models.forms

import play.api.data._
import play.api.data.Forms._

import models._
import base.Persistable
import defines.EntityType


object UserProfileF {

  final val PLACEHOLDER_TITLE = "[No Title Found]"
}

case class UserProfileF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val location: Option[String],
  val about: Option[String],
  val languages: List[String] = Nil) extends Persistable {
  val isA = EntityType.UserProfile
}


object UserProfileForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(nonEmptyText),
    		Entity.IDENTIFIER -> nonEmptyText,
    		"name" -> nonEmptyText,
    		"location" -> optional(text),
    		"about" -> optional(text),
    		"languages" -> list(text)
      )(UserProfileF.apply)(UserProfileF.unapply)
  ) 
}
