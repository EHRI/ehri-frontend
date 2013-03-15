package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._


import defines.{EntityType, PublicationStatus}
import models.base.DescribedEntity
import models._
import play.api.data.validation.ValidationError


object HistoricalAgentFormat {
  import defines.EnumWriter.enumWrites
  import models.json.IsaarFormat._
  import models.Entity._
  import models.HistoricalAgentF._

  implicit val publicationStatusReads = defines.EnumReader.enumReads(PublicationStatus)

  implicit val actorWrites: Writes[HistoricalAgentF] = new Writes[HistoricalAgentF] {
    def writes(d: HistoricalAgentF): JsValue = {
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

  implicit val actorReads: Reads[HistoricalAgentF] = (
      (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.HistoricalAgent)) andKeep
      (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      ((__ \ DATA \ NAME).read[String] orElse Reads.pure(UNNAMED_PLACEHOLDER)) and
      (__ \ DATA \ PUBLICATION_STATUS).readNullable[PublicationStatus.Value] and
      ((__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[HistoricalAgentDescriptionF]](
        Reads.list[HistoricalAgentDescriptionF]) orElse Reads.pure(Nil))
    )(HistoricalAgentF.apply _)

  implicit val actorFormat: Format[HistoricalAgentF] = Format(actorReads,actorWrites)
}