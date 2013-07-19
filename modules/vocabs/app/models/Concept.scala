package models

import base._

import defines.EntityType
import models.json._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import solr.{SolrIndexer, SolrConstants}
import akka.event.slf4j.Logger
import eu.ehri.project.definitions.Ontology

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

  val toSolr: JsObject => Seq[JsObject] = { js =>
    import SolrConstants._
    val c = js.as[Concept](Converter.restReads)
    val descriptionData = (js \ Entity.RELATIONSHIPS \ Ontology.DESCRIPTION_FOR_ENTITY)
          .asOpt[List[JsObject]].getOrElse(List.empty[JsObject])

    c.descriptions.zipWithIndex.map { case (desc, i) =>
      val data = SolrIndexer.dynamicData((descriptionData(i) \ Entity.DATA).as[JsObject])
      data ++ Json.obj(
        ID -> Json.toJson(desc.id),
        TYPE -> JsString(c.isA.toString),
        NAME_EXACT -> JsString(desc.name),
        LANGUAGE_CODE -> JsString(desc.languageCode),
        "identifier" -> c.model.identifier,
        ITEM_ID -> JsString(c.id),
        HOLDER_ID -> Json.toJson(c.vocabulary.map(_.id)),
        HOLDER_NAME -> c.toStringLang,
        ACCESSOR_FIELD -> c.accessors.map(_.id),
        LAST_MODIFIED -> c.latestEvent.map(_.model.datetime)
      )
    }
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