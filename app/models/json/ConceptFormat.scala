package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import defines.EntityType
import models.base.{AccessibleEntity, DescribedEntity}
import models._
import defines.EnumUtils._


object ConceptFormat {
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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Concept)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      ((__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[ConceptDescriptionF]](
        Reads.list[ConceptDescriptionF]) orElse Reads.pure(Nil))
    )(ConceptF.apply _)

  implicit val restFormat: Format[ConceptF] = Format(conceptReads,conceptWrites)

  private implicit val systemEventReads = SystemEventFormat.metaReads
  private implicit val vocabularyReads = VocabularyFormat.metaReads

  implicit val metaReads: Reads[ConceptMeta] = (
    __.read[JsObject] and // capture the full JS data
    __.read[ConceptF] and
    (__ \ RELATIONSHIPS \ Concept.IN_SET_REL).lazyReadNullable[List[VocabularyMeta]](
      Reads.list[VocabularyMeta]).map(_.flatMap(_.headOption)) and
    (__ \ RELATIONSHIPS \ Concept.BT_REL).lazyReadNullable[List[ConceptMeta]](
      Reads.list[ConceptMeta]).map(_.getOrElse(List.empty[ConceptMeta])) and
    (__ \ RELATIONSHIPS \ AccessibleEntity.EVENT_REL).lazyReadNullable[List[SystemEventMeta]](
      Reads.list[SystemEventMeta]).map(_.flatMap(_.headOption))
  )(ConceptMeta.apply _)
}
