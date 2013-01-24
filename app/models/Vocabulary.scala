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
case class Vocabulary(val e: Entity)
  extends AccessibleEntity
  with Formable[VocabularyF] {

  def to: VocabularyF = new VocabularyF(Some(e.id), identifier)
}
