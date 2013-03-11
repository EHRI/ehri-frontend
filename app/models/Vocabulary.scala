package models

import base._
import play.api.data._
import play.api.data.Forms._

import models.base.Persistable
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites

object VocabularyType extends Enumeration {
  type Type = Value

}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"
}

case class VocabularyF(
  val id: Option[String],
  val identifier: String,
  val name: Option[String],
  val description: Option[String]
) extends Persistable {
  val isA = EntityType.Vocabulary

  import json.VocabularyFormat._
  def toJson = Json.toJson(this)
}

object VocabularyForm {
  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      VocabularyF.NAME -> optional(nonEmptyText),
      VocabularyF.DESCRIPTION -> optional(nonEmptyText)
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}


object Vocabulary {
  final val VOCAB_REL = "inCvoc"
  final val NT_REL = "narrower"
}

case class Vocabulary(e: Entity)
  extends NamedEntity
  with AnnotatableEntity
  with Formable[VocabularyF] {

  import json.VocabularyFormat._
  def formable: VocabularyF = Json.toJson(e).as[VocabularyF]
}
