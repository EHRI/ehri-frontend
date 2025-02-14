package models

import models.json._
import play.api.libs.json._
import eu.ehri.project.definitions.Ontology
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.json.JsObject
import services.data.Constants


object ConceptF {

  val PREFLABEL = "name"
  val ALTLABEL = "altLabel"
  val HIDDENLABEL = "hiddenLabel"
  val DEFINITION = "definition"
  val NOTE = "note"
  val CHANGENOTE = "changeNote"
  val EDITORIALNOTE = "editorialNote"
  val HISTORYNOTE = "historyNote"
  val SCOPENOTE = "scopeNote"
  val URL = "url"
  val URI = "uri"
  val SEEALSO = "seeAlso"
  val LONGITUDE = "longitude"
  val LATITUDE = "latitude"
  val ACCESS_POINTS = "accessPoints"

  // Defines the sections on the form, with an empty string for the default section
  val FIELDS: Seq[(String, Seq[String])] = Seq("_" -> Seq(
    PREFLABEL,
    ALTLABEL,
    HIDDENLABEL,
    DEFINITION,
    NOTE,
    CHANGENOTE,
    EDITORIALNOTE,
    HISTORYNOTE,
    SCOPENOTE,
  ))

  // NB: Type is currently unused...
  object ConceptType extends Enumeration {
    type Type = Value
  }

  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._
  import Entity._

  implicit lazy val conceptFormat: Format[ConceptF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Concept) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ IDENTIFIER).format[String] and
    (__ \ DATA \ URI).formatNullable[String] and
    (__ \ DATA \ URL).formatNullable[String] and
    (__ \ DATA \ LONGITUDE).formatNullable[BigDecimal] and
    (__ \ DATA \ LATITUDE).formatNullable[BigDecimal] and
    (__ \ DATA \ SEEALSO).formatSeqOrSingle[String] and
    (__ \ RELATIONSHIPS \ DESCRIPTION_FOR_ENTITY).formatSeqOrEmpty[ConceptDescriptionF]
  )(ConceptF.apply, unlift(ConceptF.unapply))

  implicit object Converter extends Writable[ConceptF] {
    val _format: Format[ConceptF] = conceptFormat
  }
}

case class ConceptF(
  isA: EntityType.Value = EntityType.Concept,
  id: Option[String],
  identifier: String,
  uri: Option[String],
  url: Option[String] = None,
  longitude: Option[BigDecimal] = None,
  latitude: Option[BigDecimal] = None,
  seeAlso: Seq[String] = Nil,
  @models.relation(Ontology.DESCRIPTION_FOR_ENTITY) descriptions: Seq[ConceptDescriptionF] = Nil
) extends ModelData with Persistable with Described {

  type D = ConceptDescriptionF
}


object Concept {
  import eu.ehri.project.definitions.Ontology._
  import play.api.libs.functional.syntax._
  import DescribedModel._
  import Entity._
  import ConceptF._

  implicit lazy val _reads: Reads[Concept] = (
    __.read[ConceptF] and
    (__ \ RELATIONSHIPS \ ITEM_IN_AUTHORITATIVE_SET).readHeadNullable[Vocabulary] and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadHeadNullable[Concept](_reads) and
    (__ \ RELATIONSHIPS \ CONCEPT_HAS_BROADER).lazyReadSeqOrEmpty[Concept](_reads) and
    (__ \ RELATIONSHIPS \ IS_ACCESSIBLE_TO).readSeqOrEmpty[Accessor] and
    (__ \ RELATIONSHIPS \ ENTITY_HAS_LIFECYCLE_EVENT).readHeadNullable[SystemEvent] and
    (__ \ META).readWithDefault(Json.obj())
  )(Concept.apply _)

  implicit object ConceptResource extends ContentType[Concept]  {
    val entityType = EntityType.Concept
    val contentType = ContentTypes.Concept
    val _reads: Reads[Concept] = Concept._reads

    override def defaultParams: Seq[(String, String)] = Seq(
      Constants.INCLUDE_PROPERTIES_PARAM -> VocabularyF.NAME
    )
  }

  val form: Form[ConceptF] = Form(
    mapping(
      ISA -> ignored(EntityType.Concept),
      ID -> optional(nonEmptyText),
      IDENTIFIER -> nonEmptyText,
      URI -> optional(nonEmptyText.verifying("error.badUrl",
        url => forms.isValidUrl(url))),
      URL -> optional(nonEmptyText.verifying("error.badUrl",
        url => forms.isValidUrl(url))),
      LONGITUDE -> optional(bigDecimal),
      LATITUDE -> optional(bigDecimal),
      SEEALSO -> seq(nonEmptyText.verifying("error.badUrl",
        url => forms.isValidUrl(url))),
      DESCRIPTIONS -> seq(ConceptDescription.form.mapping)
    )(ConceptF.apply)(ConceptF.unapply)
  )
}


case class Concept(
  data: ConceptF,
  vocabulary: Option[Vocabulary],
  parent: Option[Concept] = None,
  broaderTerms: Seq[Concept] = Nil,
  accessors: Seq[Accessor] = Nil,
  latestEvent: Option[SystemEvent],
  meta: JsObject = JsObject(Seq())
) extends Model
  with DescribedModel
  with Hierarchical[Concept]
  with Accessible
  with Holder[Concept] {

  type T = ConceptF
}
