package models

import base._

import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._


object ConceptF {

  val ACCESS_POINTS = "accessPoints"
  val UNKNOWN_DATA = "unknownData"

  val LANGUAGE = "languageCode"
  val PREFLABEL = "name"
  val ALTLABEL = "altLabel"
  val DEFINITION = "definition"
  val SCOPENOTE = "scopeNote"

  // NB: Type is currently unused...
  object ConceptType extends Enumeration {
    type Type = Value
  }

  implicit object Converter extends RestConvertable[ConceptF] with ClientConvertable[ConceptF] {
    val restFormat = models.json.ConceptFormat.restFormat

    private implicit val conceptDscFmt = ConceptDescriptionF.Converter.clientFormat
    val clientFormat = Json.format[ConceptF]
  }
}

case class ConceptF(
  isA: EntityType.Value = EntityType.Concept,
  id: Option[String],
  identifier: String,
  @Annotations.Relation(Described.REL) val descriptions: List[ConceptDescriptionF] = Nil
) extends Model with Persistable with Described[ConceptDescriptionF]


object Concept {
  implicit object Converter extends ClientConvertable[Concept] with RestReadable[Concept] {
    val restReads = models.json.ConceptFormat.metaReads

    val clientFormat: Format[Concept] = (
      __.format[ConceptF](ConceptF.Converter.clientFormat) and
        (__ \ "vocabulary").formatNullable[Vocabulary](Vocabulary.Converter.clientFormat) and
        (__ \ "parent").lazyFormatNullable[Concept](clientFormat) and
        lazyNullableListFormat(__ \ "broaderTerms")(clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEvent](SystemEvent.Converter.clientFormat)
      )(Concept.apply _, unlift(Concept.unapply _))
  }
}


case class Concept(
  model: ConceptF,
  vocabulary: Option[Vocabulary],
  parent: Option[Concept] = None,
  broaderTerms: List[Concept] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEvent]
) extends AnyModel
  with MetaModel[ConceptF]
  with DescribedMeta[ConceptDescriptionF, ConceptF]
  with Hierarchical[Concept]
  with Accessible