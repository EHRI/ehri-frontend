package models.forms

import play.api.data._
import play.api.data.Forms._

import models.{Annotations, Entity}
import models.base.Persistable
import models.base.DescribedEntity
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites

object VocabularyType extends Enumeration {
  type Type = Value

}

object VocabularyF {

}

case class VocabularyF(
  val id: Option[String],
  val identifer: String
) extends Persistable {
  val isA = EntityType.Vocabulary

  def toJson = {
    import VocabularyF._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        Entity.IDENTIFIER -> identifer
      ),
      Entity.RELATIONSHIPS -> Json.obj(
      )
    )
  }
}

object VocabularyForm {
  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}
