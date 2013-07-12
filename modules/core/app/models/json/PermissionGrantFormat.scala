package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.{PermissionType, EntityType, EventType}
import org.joda.time.DateTime
import models.base.{AnyModel, MetaModel, Accessor}


object PermissionGrantFormat {
  import PermissionGrantF._
  import Entity._

  implicit val permissionTypeReads = defines.EnumUtils.enumFormat(defines.PermissionType)

  implicit val permissionGrantReads: Reads[PermissionGrantF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.PermissionGrant)) and
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ TIMESTAMP).read[String].map(new DateTime(_)) and
      (__ \ RELATIONSHIPS \ PERM_REL \\ ID).read[String].map(PermissionType.withName)
    )(PermissionGrantF.apply _)

  private implicit val accessorReads = Accessor.Converter.restReads
  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.json.UserProfileFormat.metaReads

  implicit val metaReads: Reads[PermissionGrant] = (
    __.read[PermissionGrantF] and
    (__ \ RELATIONSHIPS \ PermissionGrantF.ACCESSOR_REL).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.TARGET_REL).lazyReadNullable[List[AnyModel]](
      Reads.list[AnyModel]).map(_.getOrElse(List.empty[AnyModel])) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.SCOPE_REL).lazyReadNullable[List[AnyModel]](
      Reads.list[AnyModel]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.GRANTEE_REL).lazyReadNullable[List[UserProfile]](
      Reads.list[UserProfile]).map(_.flatMap(_.headOption))
  )(PermissionGrant.apply _)
}
