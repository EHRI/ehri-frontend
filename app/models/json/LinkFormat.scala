package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import models.LinkF
import defines.EntityType
import defines.EnumUtils._

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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Link)) andKeep
    (__ \ ID).readNullable[String] and
      ((__ \ DATA \ LINK_TYPE).read[LinkType.Value]
          orElse Reads.pure(LinkType.Associative)) and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(LinkF.apply _)

  implicit val linkFormat: Format[LinkF] = Format(linkReads,linkWrites)
}