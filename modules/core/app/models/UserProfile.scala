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


object UserProfileMeta {
  implicit object Converter extends ClientConvertable[UserProfileMeta] with RestReadable[UserProfileMeta] {

    val restReads = models.json.UserProfileFormat.metaReads
    val clientFormat: Format[UserProfileMeta] = (
      __.format[UserProfileF](UserProfileF.Converter.clientFormat) and
      nullableListFormat(__ \ "groups")(GroupMeta.Converter.clientFormat) and
      lazyNullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
    )(UserProfileMeta.quickApply _, unlift(UserProfileMeta.quickUnapply _))


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