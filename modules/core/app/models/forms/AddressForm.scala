package models.forms

import play.api.data.Form
import play.api.data.Forms._
import models.{Isdiah, AddressF, Entity}
import defines.EntityType

/**
 * Address model form.
 */
object AddressForm {

  // TODO: Move field defs to AddressF object?
  import Isdiah._

  val form = Form(
    mapping(
      Entity.ISA -> ignored(EntityType.Address),
      Entity.ID -> optional(nonEmptyText),
      ADDRESS_NAME -> optional(nonEmptyText),
      CONTACT_PERSON -> optional(nonEmptyText),
      STREET_ADDRESS -> optional(nonEmptyText),
      CITY -> optional(nonEmptyText),
      REGION -> optional(nonEmptyText),
      POSTAL_CODE -> optional(nonEmptyText),
      COUNTRY_CODE -> optional(nonEmptyText),
      EMAIL -> list(email),
      TELEPHONE -> list(nonEmptyText),
      FAX -> list(nonEmptyText),
      URL -> list(nonEmptyText verifying("error.badUrl", fields => fields match {
        case url => utils.forms.isValidUrl(url)
      }))
    )(AddressF.apply)(AddressF.unapply)
  )
}
