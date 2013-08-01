package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{AnyModel, Accessible, MetaModel, Accessor}
import eu.ehri.project.definitions.Ontology

object LinkFormat {
  import models.LinkF._
  import models.Entity._
  import Ontology._

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


  private implicit val anyModelReads = AnyModel.Converter.restReads
  private implicit val userProfileMetaReads = models.json.UserProfileFormat.metaReads
  private implicit val accessPointReads = models.json.AccessPointFormat.accessPointReads
  private implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[Link] = (
    __.read[LinkF] and
    (__ \ RELATIONSHIPS \ LINK_HAS_TARGET).lazyReadNullable[List[AnyModel]](
      Reads.list[AnyModel]).map(_.getOrElse(List.empty[AnyModel])) and
    (__ \ RELATIONSHIPS \ LINK_HAS_LINKER).lazyReadNullable[List[UserProfile]](
      Reads.list[UserProfile]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ LINK_HAS_BODY).lazyReadNullable[List[AccessPointF]](
        Reads.list[AccessPointF]).map(_.getOrElse(List.empty[AccessPointF])) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption))
  )(Link.apply _)
}