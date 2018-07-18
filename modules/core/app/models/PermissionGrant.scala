package models

import java.time.ZonedDateTime

import models.base.{Accessor, AnyModel, MetaModel, Model}
import defines.{EntityType, PermissionType}
import models.json._
import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.i18n.Messages
import services.data.{Readable, Resource}


object PermissionGrantF {
  final val TIMESTAMP = "timestamp"
  final val PERM_REL = "hasPermission"
  val ACCESSOR_REL = "hasAccessor"
  val TARGET_REL = "hasTarget"
  val GRANTEE_REL = "hasGrantee"
  val SCOPE_REL = "hasScope"

  import Entity._
  import play.api.libs.functional.syntax._

  implicit val permissionGrantReads: Reads[PermissionGrantF] = (
    (__ \ TYPE).readIfEquals(EntityType.PermissionGrant) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ TIMESTAMP).readNullable[String].map(_.map(ZonedDateTime.parse(_))) and
    (__ \ RELATIONSHIPS \ PERM_REL \\ ID).read[String].map(PermissionType.withName)
  )(PermissionGrantF.apply _)

  implicit object Converter extends Readable[PermissionGrantF] {
    val restReads: Reads[PermissionGrantF] = permissionGrantReads
  }
}

case class PermissionGrantF(
  isA: EntityType.Value = EntityType.PermissionGrant,
  id: Option[String],
  timestamp: Option[ZonedDateTime],
  permission: PermissionType.Value
) extends Model

object PermissionGrant {
  import PermissionGrantF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._

  implicit val metaReads: Reads[PermissionGrant] = (
    __.read(permissionGrantReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SUBJECT).lazyReadHeadNullable(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_TARGET).lazyReadSeqOrEmpty(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SCOPE).lazyReadHeadNullable(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_GRANTEE).readHeadNullable[UserProfile](UserProfile.UserProfileResource.restReads) and
    (__ \ META).readWithDefault(Json.obj())
  )(PermissionGrant.apply _)

  implicit object PermissionGrantResource extends Resource[PermissionGrant]  {
    implicit val restReads: Reads[PermissionGrant] = metaReads
    val entityType = EntityType.PermissionGrant
  }
}

case class PermissionGrant(
  model: PermissionGrantF,
  accessor: Option[Accessor] = None,
  targets: Seq[AnyModel] = Nil,
  scope: Option[AnyModel] = None,
  grantee: Option[UserProfile] = None,
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel {

  type T = PermissionGrantF

  override def toStringLang(implicit messages: Messages): String =
    s"<PermissionGrant: ${accessor.map(_.toStringLang)}: ${targets.headOption.map(_.toStringLang)} [${model.permission}}]>"
}
