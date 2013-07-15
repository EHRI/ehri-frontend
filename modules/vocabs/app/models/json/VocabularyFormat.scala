package models.json

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.functional._

import models._
import defines.EntityType
import defines.EnumUtils._
import models.base.{Accessible, Accessor}


object VocabularyFormat {
  import models.VocabularyF._
  import models.Entity._

  implicit val vocabularyWrites: Writes[VocabularyF] = new Writes[VocabularyF] {
    def writes(d: VocabularyF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier,
          NAME -> d.name,
          DESCRIPTION -> d.description
        )
      )
    }
  }

  implicit val vocabularyReads: Reads[VocabularyF] = (
    (__ \ TYPE).read[EntityType.Value](equalsReads(EntityType.Vocabulary)) and
    (__ \ ID).readNullable[String] and
      (__ \ DATA \ IDENTIFIER).read[String] and
      (__ \ DATA \ NAME).readNullable[String] and
      (__ \ DATA \ DESCRIPTION).readNullable[String]
    )(VocabularyF.apply _)

  implicit val restFormat: Format[VocabularyF] = Format(vocabularyReads,vocabularyWrites)

  private implicit val systemEventReads = SystemEventFormat.metaReads

  implicit val metaReads: Reads[Vocabulary] = (
    __.read[VocabularyF] and
    (__ \ RELATIONSHIPS \ Accessible.REL).lazyReadNullable[List[Accessor]](
      Reads.list(Accessor.Converter.restReads)).map(_.getOrElse(List.empty[Accessor])) and
    (__ \ RELATIONSHIPS \ Accessible.EVENT_REL).lazyReadNullable[List[SystemEvent]](
      Reads.list[SystemEvent]).map(_.flatMap(_.headOption))
  )(Vocabulary.apply _)
}
