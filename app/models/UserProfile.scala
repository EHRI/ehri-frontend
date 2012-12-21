package models

import play.api.libs.concurrent.Promise
import play.api.libs.json.JsValue
import play.api.libs.ws.WS
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import defines.{EntityType,PermissionType,ContentType}
import models.base.AccessibleEntity
import models.base.Accessor
import models.base.NamedEntity
import models.base.Formable
import models.base.Persistable
import acl._

object UserProfileRepr {
  // Have to provide a single arg constructor
  // to provide a builder function for the generic views.
  def apply(e: Entity) = new UserProfileRepr(e)
}

case class UserProfileRepr(
  val e: Entity,
  val account: Option[sql.User] = None,
  val globalPermissions: Option[GlobalPermissionSet[UserProfileRepr]] = None,
  val itemPermissions: Option[ItemPermissionSet[UserProfileRepr]] = None) extends AccessibleEntity
  with Accessor with NamedEntity with Formable[UserProfile] {
  def isAdmin = getAccessor(groups, "admin").isDefined

  def hasPermission(p: PermissionType.Value)(implicit ct: ContentType.Value): Boolean = {
    globalPermissions.map { gp =>
      if (gp.has(ct, p)) true
      else {
        itemPermissions.map { ip =>
          ip.has(p)
        }.getOrElse(false)
      }
    }.getOrElse(false)
  }

  def to: UserProfile = new UserProfile(
    id = Some(e.id),
    identifier = identifier,
    name = e.property("name").flatMap(_.asOpt[String]).getOrElse(UserProfile.PLACEHOLDER_TITLE),
    location = e.property("location").flatMap(_.asOpt[String]),
    about = e.property("about").flatMap(_.asOpt[String]),
    languages = e.property("languages").flatMap(_.asOpt[List[String]]).getOrElse(List())
  )
}

object UserProfile {

  final val PLACEHOLDER_TITLE = "[No Title Found]"
}

case class UserProfile(
  val id: Option[String],
  val identifier: String,
  val name: String,
  val location: Option[String],
  val about: Option[String],
  val languages: List[String] = Nil) extends Persistable {
  val isA = EntityType.UserProfile
}
