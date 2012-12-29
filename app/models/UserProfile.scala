package models

import defines.{PermissionType,ContentType}
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Formable
import acl._

import models.forms.UserProfileF

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
          ip.has(p)
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
    languages = e.property("languages").flatMap(_.asOpt[List[String]]).getOrElse(List())
  )
}
