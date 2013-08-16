package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{CountryF, Entity}
import defines.EntityType

/**
 * Country model form.
 */
object CountryForm {
  import CountryF._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.Country),
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText(minLength=2,maxLength=2), // ISO 2-letter field
      ABSTRACT -> optional(nonEmptyText),
      REPORT -> optional(nonEmptyText)
    )(CountryF.apply)(CountryF.unapply)
  )
}
