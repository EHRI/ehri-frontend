package models

import defines.{PermissionType,ContentType}
import acl._
import models.base._

import play.api.data._
import play.api.data.Forms._

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
  val languages: Option[List[String]] = None
) extends Persistable {
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


object UserProfile {
  // Have to provide a single arg constructor
  // to provide a builder function for the generic views.
  def apply(e: Entity) = new UserProfile(e)
}

case class UserProfile(
  val e: Entity,
  val account: Option[sql.User] = None,
  val globalPermissions: Option[GlobalPermissionSet[UserProfile]] = None,
  val itemPermissions: Option[ItemPermissionSet[UserProfile]] = None) extends AccessibleEntity
  with Accessor with NamedEntity with Formable[UserProfileF] {

  def hasPermission(ct: ContentType.Value, p: PermissionType.Value): Boolean = {
    globalPermissions.map { gp =>
      if (gp.has(ct, p)) true
      else {
        itemPermissions.map { ip =>
          ip.contentType == ct && ip.has(p)
        }.getOrElse(false)
      }
    }.getOrElse(false)
  }

  def to: UserProfileF = new UserProfileF(
    id = Some(e.id),
    identifier = identifier,
    name = e.property("name").flatMap(_.asOpt[String]).getOrElse(UserProfileF.PLACEHOLDER_TITLE),
    location = e.property("location").flatMap(_.asOpt[String]),
    about = e.property("about").flatMap(_.asOpt[String]),
    languages = e.property("languages").flatMap(_.asOpt[List[String]])
  )
}
