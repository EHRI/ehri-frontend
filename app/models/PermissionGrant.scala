package models

import models.base.{AnyModel, Model, MetaModel, Accessor}
import org.joda.time.DateTime
import defines.{PermissionType,EntityType}
import models.json.{ClientConvertable, RestReadable}



object PermissionGrantF {
  final val TIMESTAMP = "timestamp"
  final val PERM_REL = "hasPermission"
  val ACCESSOR_REL = "hasAccessor"
  val TARGET_REL = "hasTarget"
  val GRANTEE_REL = "hasGrantee"
  val SCOPE_REL = "hasScope"
}

case class PermissionGrantF(
  isA: EntityType.Value = EntityType.PermissionGrant,
  id: Option[String],
  timestamp: DateTime,
  permission: PermissionType.Value
) extends Model

object PermissionGrantMeta {
  implicit object Converter extends RestReadable[PermissionGrantMeta] with ClientConvertable[PermissionGrantMeta] {
    implicit val restReads = models.json.PermissionGrantFormat.metaReads
    implicit val clientFormat = models.json.client.permissionGrantMetaFormat
  }
}

case class PermissionGrantMeta(
  model: PermissionGrantF,
  accessor: Option[Accessor] = None,
  targets: List[AnyModel] = Nil,
  scope: Option[AnyModel] = None,
  grantee: Option[UserProfileMeta] = None
) extends AnyModel
  with MetaModel[PermissionGrantF]
