package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{HistoricalAgentF, Entity}
import defines.EntityType

/**
 * HistoricalAgent model form.
 */
object HistoricalAgentForm {

  import HistoricalAgentF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.HistoricalAgent),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2), // TODO: Increase to > 2, not done yet 'cos of test fixtures,
      PUBLICATION_STATUS -> optional(enum(defines.PublicationStatus)),
      "descriptions" -> list(IsaarForm.form.mapping)
    )(HistoricalAgentF.apply)(HistoricalAgentF.unapply)
  )
}
