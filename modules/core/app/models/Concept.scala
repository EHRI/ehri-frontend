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
  val URL = "url"
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

  implicit val conceptFormat: Format[ConceptF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Concept) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).formatSeqOrEmpty[ConceptDescriptionF]
  )(ConceptF.apply, unlift(ConceptF.unapply))

  implicit object Converter extends Writable[ConceptF] {
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
  import DescribedMeta._
  import Entity._

  private implicit val systemEventReads = SystemEvent.SystemEventResource.restReads
  private implicit val vocabularyReads = Vocabulary.VocabularyResource.restReads

  implicit val metaReads: Reads[Concept] = (
    __.read[ConceptF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).readHeadNullable[Vocabulary] and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadHeadNullable[Concept](metaReads) and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadSeqOrEmpty[Concept](metaReads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor](Accessor.Converter.restReads) and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Concept.apply _)

  implicit object ConceptResource extends backend.ContentType[Concept]  {
    val entityType = EntityType.Concept
    val contentType = ContentTypes.Concept
    val restReads = metaReads

    override def defaultParams = Seq(
      Constants.INCLUDE_PROPERTIES_PARAM -> VocabularyF.NAME
    )
  }

  val form = Form(
    mapping(
      ISA -> ignored(EntityType.Concept),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      DESCRIPTIONS -> seq(ConceptDescription.form.mapping)
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