package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{RepositoryF, Entity}
import models.base.DescribedEntity

/**
 * Repository model form.
 */
object RepositoryForm {

  import RepositoryF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures
      PUBLICATION_STATUS -> optional(models.forms.enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(IsdiahForm.form.mapping),
      PRIORITY -> optional(number(min = -1, max = 5))
    )(RepositoryF.apply)(RepositoryF.unapply)
  )
}
