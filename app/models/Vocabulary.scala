package models

import base._
import forms.VocabularyF

object Vocabulary {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

/**
 * User: mike
 * Date: 24/01/13
 */
case class Vocabulary(e: Entity)
  extends NamedEntity
  with AccessibleEntity
  with Formable[VocabularyF] {

  import VocabularyF._
  def to: VocabularyF = new VocabularyF(
      Some(e.id),
      identifier,
      e.stringProperty(NAME),
      e.stringProperty(DESCRIPTION)
  )
}
