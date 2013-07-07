package models

import defines.{PermissionType,ContentType}
import acl._
import models.base._

import base.Persistable
import defines.EntityType
import play.api.libs.json.{Reads, JsObject, Format, Json}
import defines.EnumUtils.enumWrites
import models.json.{RestReadable, ClientConvertable, RestConvertable}
import play.api.i18n.Lang


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"

  lazy implicit val userProfileFormat: Format[UserProfileF] = json.UserProfileFormat.restFormat

  implicit object Converter extends RestConvertable[UserProfileF] with ClientConvertable[UserProfileF] {
    lazy val restFormat = models.json.rest.userProfileFormat
    lazy val clientFormat = models.json.client.userProfileFormat
  }
}

case class UserProfileF(
  isA: EntityType.Value = EntityType.UserProfile,
  id: Option[String],
  identifier: String,
  name: String,
  location: Option[String] = None,
  about: Option[String] = None,
  languages: Option[List[String]] = None
) extends Model with Persistable


/*
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

  lazy val formable: UserProfileF = Json.toJson(e).as[UserProfileF]
  lazy val formableOpt: Option[UserProfileF] = Json.toJson(e).asOpt[UserProfileF]
}
*/

object UserProfileMeta {
  implicit object Converter extends ClientConvertable[UserProfileMeta] with RestReadable[UserProfileMeta] {
    val restReads = models.json.UserProfileFormat.metaReads
    val clientFormat = models.json.client.userProfileMetaFormat

    AnyModel.registerRest(EntityType.UserProfile, restReads.asInstanceOf[Reads[AnyModel]])
    AnyModel.registerClient(EntityType.UserProfile, clientFormat.asInstanceOf[Format[AnyModel]])
  }

  // Constructor, sans account and perms
  def quickApply(
     model: UserProfileF,
     groups: List[GroupMeta] = Nil,
     accessors: List[Accessor] = Nil,
     latestEvent: Option[SystemEventMeta]) = new UserProfileMeta(model, groups, accessors, latestEvent)

  def quickUnapply(up: UserProfileMeta) = Some((up.model, up.groups, up.accessors, up.latestEvent))
}


case class UserProfileMeta(
  model: UserProfileF,
  groups: List[GroupMeta] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta] = None,
  account: Option[models.sql.User] = None,
  globalPermissions: Option[GlobalPermissionSet[UserProfileMeta]] = None,
  itemPermissions: Option[ItemPermissionSet[UserProfileMeta]] = None
) extends AnyModel
  with MetaModel[UserProfileF]
  with Accessor
  with Accessible {

  override def toStringLang(implicit lang: Lang) = model.name

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
}