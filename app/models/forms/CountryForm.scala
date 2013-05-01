package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{CountryF, Entity}

/**
 * Country model form.
 */
object CountryForm {


  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      Entity.IDENTIFIER -> nonEmptyText
    )(CountryF.apply)(CountryF.unapply)
  )
}
