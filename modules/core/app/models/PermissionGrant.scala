package models

import models.base.{AnyModel, Model, MetaModel, Accessor}
import org.joda.time.DateTime
import defines.{PermissionType,EntityType}
import models.json._
import play.api.libs.json._
import play.api.libs.json.JsObject
import play.api.i18n.Lang
import backend.{BackendReadable, BackendResource}


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

  implicit object Converter extends BackendReadable[PermissionGrantF] with ClientWriteable[PermissionGrantF] {
    val restReads = permissionGrantReads
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
  import PermissionGrantF._
  import Entity._
  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._

  private implicit val accessorReads = Accessor.Converter.restReads
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.UserProfile.Converter.restReads

  implicit val metaReads: Reads[PermissionGrant] = (
    __.read(permissionGrantReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SUBJECT).lazyNullableHeadReads(Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_TARGET).lazyNullableListReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_SCOPE).lazyNullableHeadReads(AnyModel.Converter.restReads) and
    (__ \ RELATIONSHIPS \ PERMISSION_GRANT_HAS_GRANTEE).nullableHeadReads[UserProfile] and
    (__ \ META).readWithDefault(Json.obj())
  )(PermissionGrant.apply _)

  implicit object Converter extends BackendReadable[PermissionGrant] with ClientWriteable[PermissionGrant] {
    private implicit val permissionGrantFormat = Json.format[PermissionGrantF]

    implicit val restReads = metaReads
    implicit val clientFormat: Format[PermissionGrant] = (
      __.format[PermissionGrantF](PermissionGrantF.Converter.restReads) and
      (__ \ "accessor").lazyFormatNullable[Accessor](Accessor.Converter.clientFormat) and
      (__ \ "targets").nullableListFormat(AnyModel.Converter.clientFormat) and
      (__ \ "scope").lazyFormatNullable[AnyModel](AnyModel.Converter.clientFormat) and
      (__ \ "grantedBy").lazyFormatNullable[UserProfile](UserProfile.Converter.clientFormat) and
      (__ \ "meta").format[JsObject]
    )(PermissionGrant.apply, unlift(PermissionGrant.unapply))
  }

  implicit object Resource extends BackendResource[PermissionGrant] {
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
  with MetaModel[PermissionGrantF] {

  override def toStringLang(implicit lang: Lang): String =
    s"<PermissionGrant: ${accessor.map(_.toStringLang)}: ${targets.headOption.map(_.toStringLang)} [${model.permission}}]>"
}
