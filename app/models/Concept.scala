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
  final val IN_SET_REL = "inAuthoritativeSet"
  final val NT_REL = "narrower"
  final val BT_REL = "broader"
}


object ConceptMeta {
  implicit object Converter extends ClientConvertable[ConceptMeta] with RestReadable[ConceptMeta] {
    val restReads = models.json.ConceptFormat.metaReads

    val clientFormat: Format[ConceptMeta] = (
      __.format[ConceptF](ConceptF.Converter.clientFormat) and
        (__ \ "vocabulary").formatNullable[VocabularyMeta](VocabularyMeta.Converter.clientFormat) and
        (__ \ "parent").lazyFormatNullable[ConceptMeta](clientFormat) and
        lazyNullableListFormat(__ \ "broaderTerms")(clientFormat) and
        nullableListFormat(__ \ "accessibleTo")(Accessor.Converter.clientFormat) and
        (__ \ "event").formatNullable[SystemEventMeta](SystemEventMeta.Converter.clientFormat)
      )(ConceptMeta.apply _, unlift(ConceptMeta.unapply _))


  }
}


case class ConceptMeta(
  model: ConceptF,
  vocabulary: Option[VocabularyMeta],
  parent: Option[ConceptMeta] = None,
  broaderTerms: List[ConceptMeta] = Nil,
  accessors: List[Accessor] = Nil,
  latestEvent: Option[SystemEventMeta]
) extends AnyModel
  with MetaModel[ConceptF]
  with DescribedMeta[ConceptDescriptionF, ConceptF]
  with Hierarchical[ConceptMeta]
  with Accessible