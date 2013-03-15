package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{HistoricalAgentF, Entity}
import models.base.DescribedEntity

/**
 * HistoricalAgent model form.
 */
object HistoricalAgentForm {

  import HistoricalAgentF._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText,
      NAME -> nonEmptyText,
      PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      DescribedEntity.DESCRIPTIONS -> list(IsaarForm.form.mapping)
    )(HistoricalAgentF.apply)(HistoricalAgentF.unapply)
  )
}
