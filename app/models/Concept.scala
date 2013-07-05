package models

import base._

import defines.EntityType
import models.json.{RestReadable, ClientConvertable, RestConvertable}

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
    lazy val restFormat = models.json.rest.conceptFormat
    lazy val clientFormat = models.json.client.conceptFormat
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
    val clientFormat = models.json.client.conceptMetaFormat
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