package models.json

import play.api.libs.json._
import models._
import play.api.libs.functional.syntax._
import defines.{PermissionType, EntityType, EventType}
import org.joda.time.DateTime
import models.base.{MetaModel, Accessor}


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
  private implicit val metaModelReads = MetaModel.Converter.restReads
  private implicit val userProfileMetaReads = models.json.UserProfileFormat.metaReads

  implicit val metaReads: Reads[PermissionGrantMeta] = (
    __.read[PermissionGrantF] and
    (__ \ RELATIONSHIPS \ PermissionGrantF.ACCESSOR_REL).lazyReadNullable[List[Accessor]](
      Reads.list[Accessor]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.TARGET_REL).lazyReadNullable[List[MetaModel[_]]](
      Reads.list[MetaModel[_]]).map(_.getOrElse(List.empty[MetaModel[_]])) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.SCOPE_REL).lazyReadNullable[List[MetaModel[_]]](
      Reads.list[MetaModel[_]]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ PermissionGrantF.GRANTEE_REL).lazyReadNullable[List[UserProfileMeta]](
      Reads.list[UserProfileMeta]).map(_.flatMap(_.headOption))
  )(PermissionGrantMeta.apply _)
}
