package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import defines.PublicationStatus
import models.base.DescribedEntity
import models._


object ConceptFormat {
  import defines.EnumWriter.enumWrites
  import models.json.ConceptDescriptionFormat._
  import models.Entity._

  implicit val conceptWrites: Writes[ConceptF] = new Writes[ConceptF] {
    def writes(d: ConceptF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier
        ),
        RELATIONSHIPS -> Json.obj(
          DescribedEntity.DESCRIBES_REL -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val conceptReads: Reads[ConceptF] = (
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[ConceptDescriptionF]](
        Reads.list[ConceptDescriptionF])
    )(ConceptF.apply _)

  implicit val conceptFormat: Format[ConceptF] = Format(conceptReads,conceptWrites)
}