package models

import defines.{PermissionType,ContentTypes}
import acl._
import models.base._

import base.Persistable
import defines.EntityType
import play.api.libs.json._
import models.json._
import play.api.i18n.Lang
import play.api.libs.functional.syntax._
import play.api.data.Form
import play.api.data.Forms._
import utils.forms._
import play.api.libs.json.JsObject
import eu.ehri.project.definitions.Ontology


object UserProfileF {

  val FIELD_PREFIX = "profile"

  final val PLACEHOLDER_TITLE = "[No Title Found]"

  val NAME = "name"
  val LOCATION = "location"
  val ABOUT = "about"
  val LANGUAGES = "languages"
  val IMAGE_URL = "imageUrl"
  val ACTIVE = "active"
  val STAFF = "staff"

  import Entity._

  implicit val userProfileWrites: Writes[UserProfileF] = new Writes[UserProfileF] {
    def writes(d: UserProfileF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          LOCATION -> d.location,
          ABOUT -> d.about,
          LANGUAGES -> d.languages,
          IMAGE_URL -> d.imageUrl,
          ACTIVE -> d.active
        )
      )
    }
  }

  implicit val userProfileReads: Reads[UserProfileF] = (
    (__ \ TYPE).readIfEquals(EntityType.UserProfile) and
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).read[String] and
      (__ \ DATA \ LOCATION).readNullable[String] and
      (__ \ DATA \ ABOUT).readNullable[String] and
      (__ \ DATA \ LANGUAGES).readListOrSingle[String] and
      (__ \ DATA \ IMAGE_URL).readNullable[String] and
      (__ \ DATA \ ACTIVE).readNullable[Boolean].map(_.getOrElse(true))
    )(UserProfileF.apply _)

  implicit val userProfileFormat: Format[UserProfileF] = Format(userProfileReads,userProfileWrites)

  implicit object Converter extends RestConvertable[UserProfileF] with ClientConvertable[UserProfileF] {
    lazy val restFormat = userProfileFormat
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
  imageUrl: Option[String] = None,
  active: Boolean = true
) extends Model with Persistable


object UserProfile {
  import UserProfileF._
  import Entity._
  import Ontology._

  private implicit val groupReads = Group.Converter.restReads
  private implicit val systemEventReads = SystemEvent.Converter.restReads

  implicit val metaReads: Reads[UserProfile] = (
    __.read[UserProfileF] and
    (__ \ RELATIONSHIPS \ ACCESSOR_BELONGS_TO_GROUP).lazyNullableListReads(groupReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyNullableListReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyNullableHeadReads(
      SystemEvent.Converter.restReads) and
    (__ \ META).readNullable[JsObject].map(_.getOrElse(JsObject(Seq())))
  )(UserProfile.quickApply _)

  implicit object Converter extends ClientConvertable[UserProfile] with RestReadable[UserProfile] {

    val restReads = metaReads
    val clientFormat: Format[UserProfile] = (
      __.format[UserProfileF](UserProfileF.Converter.clientFormat) and
      (__ \ "groups").nullableListFormat(Group.Converter.clientFormat) and
      (__ \ "accessibleTo").lazyNullableListFormat(Accessor.Converter.clientFormat) and
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

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.UserProfile),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> nonEmptyText,
      LOCATION -> optional(text),
      ABOUT -> optional(text),
      LANGUAGES -> list(nonEmptyText),
      IMAGE_URL -> optional(nonEmptyText.verifying(s => isValidUrl(s))),
      ACTIVE -> boolean
    )(UserProfileF.apply)(UserProfileF.unapply)
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