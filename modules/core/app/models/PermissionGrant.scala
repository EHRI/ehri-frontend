package models

import models.base.{AnyModel, Model, MetaModel, Accessor}
import org.joda.time.DateTime
import defines.{PermissionType,EntityType}
import models.json._
import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.i18n.Lang
import backend.{Entity, Readable, Resource}


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
    (__ \ DATA \ TIMESTAMP).readNullable[String].map(_.map(new DateTime(_))) and
    (__ \ RELATIONSHIPS \ PERM_REL \\ ID).read[String].map(PermissionType.withName)
  )(PermissionGrantF.apply _)

  implicit object Converter extends Readable[PermissionGrantF] {
    val restReads = permissionGrantReads
  }
}

case class PermissionGrantF(
  isA: EntityType.Value = EntityType.PermissionGrant,
  id: Option[String],
  timestamp: Option[DateTime],
  permission: PermissionType.Value
) extends Model

object PermissionGrant {
  import PermissionGrantF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._

  private implicit val accessorReads = Accessor.Converter.restReads
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.UserProfile.UserProfileResource.restReads

  implicit val metaReads: Reads[PermissionGrant] = (
    __.read(permissionGrantReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SUBJECT).lazyNullableHeadReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_TARGET).lazyNullableSeqReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SCOPE).lazyNullableHeadReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_GRANTEE).nullableHeadReads[UserProfile] and
    (__ \ META).readWithDefault(Json.obj())
  )(PermissionGrant.apply _)

  implicit object PermissionGrantResource extends Resource[PermissionGrant]  {
    implicit val restReads = metaReads
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
  with MetaModel[PermissionGrantF] {

  override def toStringLang(implicit lang: Lang): String =
    s"<PermissionGrant: ${accessor.map(_.toStringLang)}: ${targets.headOption.map(_.toStringLang)} [${model.permission}}]>"
}
