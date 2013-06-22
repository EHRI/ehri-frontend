package models

import base._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.{Format, Json}
import defines.EnumUtils.enumWrites
import models.json.{ClientConvertable, RestConvertable}

object VocabularyType extends Enumeration {
  type Type = Value
}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"

  lazy implicit val vocabularyFormat: Format[VocabularyF] = json.VocabularyFormat.restFormat

  implicit object Converter extends RestConvertable[VocabularyF] with ClientConvertable[VocabularyF] {
    lazy val restFormat = models.json.rest.vocabularyFormat
    lazy val clientFormat = models.json.client.vocabularyFormat
  }
}


case class VocabularyF(
  isA: EntityType.Value = EntityType.Vocabulary,
  id: Option[String],
  identifier: String,
  name: Option[String],
  description: Option[String]
) extends Persistable


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
