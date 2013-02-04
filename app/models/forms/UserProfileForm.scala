package models.forms

import play.api.data._
import play.api.data.Forms._

import models._
import base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites


object UserProfileF {

  final val PLACEHOLDER_TITLE = "[No Title Found]"
}

case class UserProfileF(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val location: Option[String] = None,
  val about: Option[String] = None,
  val languages: Option[List[String]] = None) extends Persistable {
  val isA = EntityType.UserProfile

  import Entity._
  def toJson = Json.obj(
    ID -> id,
    TYPE -> isA,
    DATA -> Json.obj(
      IDENTIFIER -> identifier,
      "name" -> name,
      "location" -> location,
      "about" -> about,
      "languages" -> languages
    )
  )
}


object UserProfileForm {

  val form = Form(
      mapping(
    		Entity.ID -> optional(nonEmptyText),
    		Entity.IDENTIFIER -> nonEmptyText,
    		"name" -> nonEmptyText,
    		"location" -> optional(nonEmptyText),
    		"about" -> optional(nonEmptyText),
    		"languages" -> optional(list(nonEmptyText))
      )(UserProfileF.apply)(UserProfileF.unapply)
  ) 
}
