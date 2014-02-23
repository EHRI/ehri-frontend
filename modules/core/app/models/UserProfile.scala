package models

import defines.{PermissionType,ContentTypes}
import acl._
import models.base._

import base.Persistable
import defines.EntityType
import play.api.libs.json._
import defines.EnumUtils.enumWrites
import models.json._
import play.api.i18n.Lang
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import scala.Some
import play.api.libs.json.JsObject
import utils.forms._
import scala.Some
import play.api.libs.json.JsObject


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"
  val IMAGE_URL = "imageUrl"

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
  languages: List[String] = Nil,
  imageUrl: Option[String] = None
) extends Model with Persistable


object UserProfile {
  implicit object Converter extends ClientConvertable[UserProfile] with RestReadable[UserProfile] {

    val restReads = models.json.UserProfileFormat.metaReads
    val clientFormat: Format[UserProfile] = (
      __.format[UserProfileF](UserProfileF.Converter.clientFormat) and
      nullableListFormat(__ \ "groups")(Group.Converter.clientFormat) and
      lazyNullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
      (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(UserProfile.quickApply _, unlift(UserProfile.quickUnapply _))
  }

  implicit object Resource extends RestResource[UserProfile] {
    val entityType = EntityType.UserProfile
  }

  // Constructor, sans account and perms
  def quickApply(
     model: UserProfileF,
     groups: List[Group] = Nil,
     accessors: List[Accessor] = Nil,
     latestEvent: Option[SystemEvent],
     meta: JsObject) = new UserProfile(model, groups, accessors, latestEvent, meta)

  def quickUnapply(up: UserProfile) = Some((up.model, up.groups, up.accessors, up.latestEvent, up.meta))

  import UserProfileF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.UserProfile),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> list(nonEmptyText),
      IMAGE_URL -> optional(nonEmptyText.verifying(s => isValidUrl(s))))
      (UserProfileF.apply)(UserProfileF.unapply)
  )
}


case class UserProfile(
  model: UserProfileF,
  groups: List[Group] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent] = None,
  meta: JsObject = JsObject(Seq()),
  account: Option[Account] = None,
  globalPermissions: Option[GlobalPermissionSet[UserProfile]] = None,
  itemPermissions: Option[ItemPermissionSet[UserProfile]] = None
) extends AnyModel
  with MetaModel[UserProfileF]
  with Accessor
  with Accessible {

  override def toStringLang(implicit lang: Lang) = model.name

  def hasPermission(ct: ContentTypes.Value, p: PermissionType.Value): Boolean = {
    globalPermissions.exists(gp =>
      if (gp.has(ct, p)) true
      else {
        itemPermissions.exists(ip => ip.contentType == ct && ip.has(p))
      })
  }

  def followerCount = meta.fields.find(_._1 == "followers").flatMap(_._2.asOpt[Int]).getOrElse(0)
  def followingCount = meta.fields.find(_._1 == "following").flatMap(_._2.asOpt[Int]).getOrElse(0)
}