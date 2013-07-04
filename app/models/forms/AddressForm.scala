package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{Isdiah, AddressF, Entity}

/**
 * Address model form.
 */
object AddressForm {

  // TODO: Move field defs to AddressF object?
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ID -> optional(nonEmptyText),
      ADDRESS_NAME -> optional(nonEmptyText),
      CONTACT_PERSON -> optional(nonEmptyText),
      STREET_ADDRESS -> optional(nonEmptyText),
      CITY -> optional(nonEmptyText),
      REGION -> optional(nonEmptyText),
      POSTAL_CODE -> optional(nonEmptyText),
      COUNTRY_CODE -> optional(nonEmptyText),
      EMAIL -> list(email),
      TELEPHONE -> list(text),
      FAX -> list(text),
      URL -> list(text)
    )(AddressF.apply)(AddressF.unapply)
  )
}
