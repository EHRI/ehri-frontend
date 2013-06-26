package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{AccessibleEntity, MetaModel, Accessor}
import models.AccessPoint

object LinkFormat {
  import models.LinkF._
  import models.Entity._

  implicit val linkTypeReads = enumReads(LinkType)

  implicit val linkWrites: Writes[LinkF] = new Writes[LinkF] {
    def writes(d: LinkF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          LINK_TYPE -> d.linkType,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val linkReads: Reads[LinkF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Link)) and
    (__ \ ID).readNullable[String] and
      ((__ \ DATA \ LINK_TYPE).read[LinkType.Value]
          orElse Reads.pure(LinkType.Associative)) and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(LinkF.apply _)

  implicit val restFormat: Format[LinkF] = Format(linkReads,linkWrites)


  private implicit val metaModelReads = MetaModel.Converter.restReads
  private implicit val userProfileMetaReads = models.json.UserProfileFormat.metaReads
  private implicit val accessPointReads = models.json.AccessPointFormat.accessPointReads
  private implicit val accessorReads = Accessor.Converter.restReads

  implicit val metaReads: Reads[LinkMeta] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LinkF.LINK_REL).lazyReadNullable[List[MetaModel[_]]](
      Reads.list[MetaModel[_]]).map(_.getOrElse(List.empty[MetaModel[_]])) and
    (__ \ RELATIONSHIPS \ LinkF.ACCESSOR_REL).lazyReadNullable[List[UserProfileMeta]](
      Reads.list[UserProfileMeta]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ LinkF.BODY_REL).lazyReadNullable[List[AccessPointF]](
        Reads.list[AccessPointF]).map(_.getOrElse(List.empty[AccessPointF])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.ACCESS_REL).lazyReadNullable[List[Accessor]](
      Reads.list[Accessor]).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
  )(LinkMeta.apply _)
}