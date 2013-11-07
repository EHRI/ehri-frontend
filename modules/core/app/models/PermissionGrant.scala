package models

import models.base.{AnyModel, Model, MetaModel, Accessor}
import org.joda.time.DateTime
import defines.{PermissionType,EntityType}
import models.json.{RestResource, ClientConvertable, RestReadable}
import play.api.libs.json._
import play.api.libs.functional.syntax._


object PermissionGrantF {
  final val TIMESTAMP = "timestamp"
  final val PERM_REL = "hasPermission"
  val ACCESSOR_REL = "hasAccessor"
  val TARGET_REL = "hasTarget"
  val GRANTEE_REL = "hasGrantee"
  val SCOPE_REL = "hasScope"

  implicit object Converter extends RestReadable[PermissionGrantF] with ClientConvertable[PermissionGrantF] {
    val restReads = models.json.PermissionGrantFormat.permissionGrantReads
    val clientFormat = Json.format[PermissionGrantF]
  }
}

case class PermissionGrantF(
  isA: EntityType.Value = EntityType.PermissionGrant,
  id: Option[String],
  timestamp: Option[DateTime],
  permission: PermissionType.Value
) extends Model

object PermissionGrant {
  implicit object Converter extends RestReadable[PermissionGrant] with ClientConvertable[PermissionGrant] {
    private implicit val permissionGrantFormat = Json.format[PermissionGrantF]

    implicit val restReads = models.json.PermissionGrantFormat.metaReads
    implicit val clientFormat: Format[PermissionGrant] = (
      __.format[PermissionGrantF](PermissionGrantF.Converter.restReads) and
      (__ \ "accessor").lazyFormatNullable[Accessor](Accessor.Converter.clientFormat) and
      json.nullableListFormat((__ \ "targets"))(AnyModel.Converter.clientFormat) and
      (__ \ "scope").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
      (__ \ "grantedBy").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(PermissionGrant.apply _, unlift(PermissionGrant.unapply _))
  }

  implicit object Resource extends RestResource[PermissionGrant] {
    val entityType = EntityType.PermissionGrant
  }
}

case class PermissionGrant(
  model: PermissionGrantF,
  accessor: Option[Accessor] = None,
  targets: List[AnyModel] = Nil,
  scope: Option[AnyModel] = None,
  grantee: Option[UserProfile] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[PermissionGrantF]
