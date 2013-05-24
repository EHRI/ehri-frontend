package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{VocabularyF, Entity}

/**
 * Vocabulary model form.
 */
object VocabularyForm {

  import VocabularyF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=3),
      NAME -> optional(nonEmptyText),
      DESCRIPTION -> optional(nonEmptyText)
    )(VocabularyF.apply)(VocabularyF.unapply)
  )
}
