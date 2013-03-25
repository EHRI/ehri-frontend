package models

import base._

import models.base.Persistable
import models.base.DescribedEntity
import defines.EntityType
import play.api.libs.json.Json
import play.api.i18n.Lang

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
}

case class ConceptF(
  val id: Option[String],
  val identifier: String,
  @Annotations.Relation(DescribedEntity.DESCRIBES_REL) val descriptions: List[ConceptDescriptionF] = Nil
) extends Persistable {
  val isA = EntityType.Concept

  import json.ConceptFormat._
  def toJson = Json.toJson(this)
}

object Concept {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

/**
 * User: mike
 * Date: 24/01/13
 */
case class Concept(e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with AnnotatableEntity
  with DescribedEntity
  with HierarchicalEntity[Concept]
  with Formable[ConceptF] {

  val hierarchyRelationName = Concept.NT_REL

  override val nameProperty = ConceptF.PREFLABEL

  override lazy val descriptions: List[ConceptDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(ConceptDescription(_))
  lazy val vocabulary: Option[Vocabulary] = e.relations(Concept.VOCAB_REL).headOption.map(Vocabulary(_))
  lazy val broaderTerms: List[Concept] = e.relations(Concept.NT_REL).map(Concept(_))

  import json.ConceptFormat._
  lazy val formable: ConceptF = Json.toJson(e).as[ConceptF]
  lazy val formableOpt: Option[ConceptF] = Json.toJson(e).asOpt[ConceptF]

  // Because we (currently) have no 'name' property on Concept, get the first available preflabel
  override def toString = descriptions.headOption.flatMap(_.stringProperty(ConceptF.PREFLABEL)).getOrElse(identifier)

  // Language-aware toString
  def toString(implicit lang: Lang) = descriptions
    .find(_.formable.languageCode==lang.code).orElse(descriptions.headOption)
    .flatMap(_.stringProperty(ConceptF.PREFLABEL)).getOrElse(identifier)
}
