package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._

import defines.EntityType
import models.base.DescribedEntity
import models._
import defines.EnumUtils._


object ConceptFormat extends JsonConverter[ConceptF] {
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
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Concept)) andKeep
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      ((__ \ RELATIONSHIPS \ DescribedEntity.DESCRIBES_REL).lazyRead[List[ConceptDescriptionF]](
        Reads.list[ConceptDescriptionF]) orElse Reads.pure(Nil))
    )(ConceptF.apply _)

  implicit val restFormat: Format[ConceptF] = Format(conceptReads,conceptWrites)
}

//object ConceptMetaFormat extends JsonConverter[ConceptMetaF] {
//  import models.Entity._
//  import VocabularyMetaReads.vocabularyMetaReads
//
//  implicit val conceptMetaReads: Reads[ConceptMeta] = (
//    (__ \ ID).read[String] and
//    (__).read[ConceptF] and
//      ((__ \ RELATIONSHIPS \ Concept.IN_SET_REL).lazyRead(Reads.list[VocabularyMeta])
//        orElse Reads.pure(Nil)) and
//      ((__ \ RELATIONSHIPS \ Concept.BT_REL).lazyRead(Reads.list[ConceptMeta]) orElse Reads.pure(Nil))
//  )(ConceptMeta.apply _)
//}