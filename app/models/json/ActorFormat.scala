package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._


import defines.PublicationStatus
import models.base.DescribedEntity
import models._


object ActorFormat {
  import defines.EnumWriter.enumWrites
  import models.json.IsaarFormat._
  import models.Entity._
  import models.ActorF._

  implicit val publicationStatusReads = defines.EnumReader.enumReads(PublicationStatus)

  implicit val actorWrites: Writes[ActorF] = new Writes[ActorF] {
    def writes(d: ActorF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          PUBLICATION_STATUS -> d.publicationStatus
        ),
        RELATIONSHIPS -> Json.obj(
          DESC_REL -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val actorReads: Reads[ActorF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      ((__ \ DATA \ NAME).read[String] orElse Reads.pure(UNNAMED_PLACEHOLDER)) and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      ((__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[ActorDescriptionF]](
        Reads.list[ActorDescriptionF]) orElse Reads.pure(Nil))
    )(ActorF.apply _)

  implicit val actorFormat: Format[ActorF] = Format(actorReads,actorWrites)
}