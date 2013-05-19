package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumUtils.enumWrites

object VocabularyType extends Enumeration {
  type Type = Value
}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val vocabularyFormat = json.VocabularyFormat.vocabularyFormat
}


case class VocabularyF(
  val id: Option[String],
  val identifier: String,
  val name: Option[String],
  val description: Option[String]
) extends Persistable {
  val isA = EntityType.Vocabulary

  def toJson = Json.toJson(this)
}


object Vocabulary {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

case class Vocabulary(e: Entity)
  extends NamedEntity
  with AnnotatableEntity
  with Formable[VocabularyF] {

  lazy val formable: VocabularyF = Json.toJson(e).as[VocabularyF]
  lazy val formableOpt: Option[VocabularyF] = Json.toJson(e).asOpt[VocabularyF]
}
