package models

import defines.{PermissionType,ContentType}
import acl._
import models.base._

import base.Persistable
import defines.EntityType
import play.api.libs.json._
import defines.EnumUtils.enumWrites
import models.json._
import play.api.i18n.Lang
import scala.Some
import scala.Some
import scala.Some
import play.api.libs.functional.syntax._
import scala.Some


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"

  implicit object Converter extends RestConvertable[UserProfileF] with ClientConvertable[UserProfileF] {
    lazy val restFormat = models.json.UserProfileFormat.restFormat
    lazy val clientFormat = Json.format[UserProfileF]
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


object UserProfile {
  implicit object Converter extends ClientConvertable[UserProfile] with RestReadable[UserProfile] {

    val restReads = models.json.UserProfileFormat.metaReads
    val clientFormat: Format[UserProfile] = (
      __.format[UserProfileF](UserProfileF.Converter.clientFormat) and
      nullableListFormat(__ \ "groups")(Group.Converter.clientFormat) and
      lazyNullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
    )(UserProfile.quickApply _, unlift(UserProfile.quickUnapply _))


  }

  // Constructor, sans account and perms
  def quickApply(
     model: UserProfileF,
     groups: List[Group] = Nil,
     accessors: List[Accessor] = Nil,
     latestEvent: Option[SystemEvent]) = new UserProfile(model, groups, accessors, latestEvent)

  def quickUnapply(up: UserProfile) = Some((up.model, up.groups, up.accessors, up.latestEvent))
}


case class UserProfile(
  model: UserProfileF,
  groups: List[Group] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  account: Option[models.sql.User] = None,
  globalPermissions: Option[GlobalPermissionSet[UserProfile]] = None,
  itemPermissions: Option[ItemPermissionSet[UserProfile]] = None
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