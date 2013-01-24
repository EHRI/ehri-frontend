package models.forms

import play.api.data._
import play.api.data.Forms._

import models.{forms, Annotations, Entity}
import models.base.Persistable
import models.base.DescribedEntity
import defines.EntityType
import play.api.libs.json.Json
import defines.EnumWriter.enumWrites
import org.mockito.exceptions.misusing.NotAMockException

object VocabularyType extends Enumeration {
  type Type = Value

}

object VocabularyF {
  val NAME = "name"
  val DESCRIPTION = "description"
}

case class VocabularyF(
  val id: Option[String],
  val identifer: String,
  val name: Option[String],
  val description: Option[String]
) extends Persistable {
  val isA = EntityType.Vocabulary

  def toJson = {
    import VocabularyF._

    Json.obj(
      Entity.ID -> id,
      Entity.TYPE -> isA,
      Entity.DATA -> Json.obj(
        Entity.IDENTIFIER -> identifer,
        NAME -> name,
        DESCRIPTION -> description
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
      Entity.IDENTIFIER -> nonEmptyText,
      VocabularyF.NAME -> optional(nonEmptyText),
      VocabularyF.DESCRIPTION -> optional(nonEmptyText)
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}
