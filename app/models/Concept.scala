package models

import base._
import forms.{ConceptDescriptionF, ConceptF}

object Concept {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

/**
 * User: mike
 * Date: 24/01/13
 */
case class Concept(val e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with DescribedEntity
  with Formable[ConceptF] {
  override def descriptions: List[ConceptDescription] = e.relations(DescribedEntity.DESCRIBES_REL).map(ConceptDescription(_))
  val broader: Option[Concept] = e.relations(Concept.NT_REL).headOption.map(Concept(_))

  def to: ConceptF = new ConceptF(Some(e.id), identifier, descriptions.map(_.to))
}

case class ConceptDescription(val e: Entity)
  extends Description
  with Formable[ConceptDescriptionF] {
  def broader: Option[Concept] = e.relations(Concept.NT_REL).headOption.map(Concept(_))


  import ConceptF._

  def to: ConceptDescriptionF = new ConceptDescriptionF(
    id = Some(e.id),
    languageCode = e.stringProperty(LANGUAGE).getOrElse(sys.error(s"No language code found on concept data: ${e}")),
    prefLabel = e.stringProperty(PREFLABEL).getOrElse(sys.error(s"No prefLabel found on concept data: ${e}")),
    altLabels = e.listProperty(ALTLABEL),
    definition = e.stringProperty(DEFINITION),
    scopeNote = e.stringProperty(SCOPENOTE)
  )
}
