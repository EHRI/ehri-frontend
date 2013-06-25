package models

import base._

import models.base.Persistable
import models.base.DescribedEntity
import defines.EntityType
import play.api.libs.json.{JsObject, Format, Json}
import play.api.i18n.Lang
import models.json.{RestReadable, ClientConvertable, RestConvertable}

object ConceptF {

  val LANGUAGE = "languageCode"
  val PREFLABEL = "name"
  val ALTLABEL = "altLabel"
  val DEFINITION = "definition"
  val SCOPENOTE = "scopeNote"

  // NB: Type is currently unused...
  object ConceptType extends Enumeration {
    type Type = Value
  }

  implicit val conceptFormat: Format[ConceptF] = json.ConceptFormat.restFormat

  implicit object Converter extends RestConvertable[ConceptF] with ClientConvertable[ConceptF] {
    lazy val restFormat = models.json.rest.conceptFormat
    lazy val clientFormat = models.json.client.conceptFormat
  }
}

case class ConceptF(
  isA: EntityType.Value = EntityType.Concept,
  id: Option[String],
  identifier: String,
  @Annotations.Relation(DescribedEntity.DESCRIBES_REL) val descriptions: List[ConceptDescriptionF] = Nil
) extends Persistable


object Concept {
  final val IN_SET_REL = "inAuthoritativeSet"
  final val NT_REL = "narrower"
  final val BT_REL = "broader"
}

/**
 * User: mike
 * Date: 24/01/13
 */
case class Concept(e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with LinkableEntity
  with DescribedEntity[ConceptDescription]
  with HierarchicalEntity[Concept]
  with Formable[ConceptF] {

  val hierarchyRelationName = Concept.NT_REL

  override val nameProperty = ConceptF.PREFLABEL

  lazy val descriptions: List[ConceptDescription] = e.relations(DescribedEntity.DESCRIBES_REL)
      .map(ConceptDescription(_)).sortBy(d => d.languageCode)
  lazy val vocabulary: Option[Vocabulary] = e.relations(Concept.IN_SET_REL).headOption.map(Vocabulary(_))
  lazy val broaderTerms: List[Concept] = e.relations(Concept.BT_REL).map(Concept(_))

  lazy val formable: ConceptF = Json.toJson(e).as[ConceptF]
  lazy val formableOpt: Option[ConceptF] = Json.toJson(e).asOpt[ConceptF]

  // Because we (currently) have no 'name' property on Concept, get the first available preflabel
  override def toString = descriptions.headOption.flatMap(_.stringProperty(ConceptF.PREFLABEL))
      .orElse(e.stringProperty(Entity.IDENTIFIER)).getOrElse(e.id)
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
  broaderTerms: List[ConceptMeta],
  latestEvent: Option[SystemEventMeta]
) extends MetaModel[ConceptF]