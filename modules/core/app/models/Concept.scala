package models

import base._

import defines.{ContentTypes, EntityType}
import models.json._
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import backend.rest.Constants
import backend._
import play.api.libs.json.JsObject


object ConceptF {

  val PREFLABEL = "name"
  val ALTLABEL = "altLabel"
  val DEFINITION = "definition"
  val SCOPENOTE = "scopeNote"
  
  val LONGITUDE = "longitude"
  val LATITUDE = "latitude"
  val ACCESS_POINTS = "accessPoints"

  // NB: Type is currently unused...
  object ConceptType extends Enumeration {
    type Type = Value
  }

  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._
  import Entity._

  implicit val conceptWrites: Writes[ConceptF] = new Writes[ConceptF] {
    def writes(d: ConceptF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          IDENTIFIER -> d.identifier
        ),
        RELATIONSHIPS -> Json.obj(
          DESCRIPTION_FOR_ENTITY -> Json.toJson(d.descriptions.map(Json.toJson(_)).toSeq)
        )
      )
    }
  }

  implicit val conceptReads: Reads[ConceptF] = (
    (__ \ TYPE).readIfEquals(EntityType.Concept) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ IDENTIFIER).read[String] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).nullableSeqReads[ConceptDescriptionF]
  )(ConceptF.apply _)

  implicit val conceptFormat: Format[ConceptF] = Format(conceptReads,conceptWrites)


  implicit object Converter extends BackendWriteable[ConceptF] {
    val restFormat = conceptFormat
  }
}

case class ConceptF(
  isA: EntityType.Value = EntityType.Concept,
  id: Option[String],
  identifier: String,
  @models.relation(Ontology.DESCRIPTION_FOR_ENTITY) descriptions: Seq[ConceptDescriptionF] = Nil
) extends Model with Persistable with Described[ConceptDescriptionF]


object Concept {
  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._
  import Entity._

  private implicit val systemEventReads = SystemEvent.Resource.restReads
  private implicit val vocabularyReads = Vocabulary.Resource.restReads

  implicit val metaReads: Reads[Concept] = (
    __.read[ConceptF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).nullableHeadReads[Vocabulary] and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyNullableHeadReads[Concept](metaReads) and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyNullableSeqReads[Concept](metaReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).nullableSeqReads[Accessor](Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).nullableHeadReads[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Concept.apply _)

  implicit object Resource extends BackendContentType[Concept] {
    val entityType = EntityType.Concept
    val contentType = ContentTypes.Concept
    val restReads = metaReads

    override def defaultParams = Seq(
      Constants.INCLUDE_PROPERTIES_PARAM -> VocabularyF.NAME
    )
  }

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.Concept),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      "descriptions" -> seq(ConceptDescription.form.mapping)
    )(ConceptF.apply)(ConceptF.unapply)
  )
}


case class Concept(
  model: ConceptF,
  vocabulary: Option[Vocabulary],
  parent: Option[Concept] = None,
  broaderTerms: Seq[Concept] = Nil,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends AnyModel
  with MetaModel[ConceptF]
  with DescribedMeta[ConceptDescriptionF, ConceptF]
  with Hierarchical[Concept]
  with Accessible
  with Holder[Concept]