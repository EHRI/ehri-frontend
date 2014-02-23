package models

import defines.EntityType
import models.base.Model
import play.api.libs.json.Json
import models.json.{ClientConvertable, RestConvertable}
import play.api.data.Form
import play.api.data.Forms._


object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"

  implicit object Converter extends RestConvertable[AddressF] with ClientConvertable[AddressF] {
    val restFormat = models.json.AddressFormat.restFormat
    val clientFormat = Json.format[AddressF]
  }
}


case class AddressF(
  isA: EntityType.Value = EntityType.Address,
  id: Option[String],
  name: Option[String],
  contactPerson: Option[String] = None,
  streetAddress: Option[String] = None,
  city: Option[String] = None,
  region: Option[String] = None,
  postalCode: Option[String] = None,
  countryCode: Option[String] = None,
  email: List[String] = Nil,
  telephone: List[String] = Nil,
  fax: List[String] = Nil,
  url: List[String] = Nil
  ) extends Model {

  override def toString
      = List(name, contactPerson,streetAddress,city).filter(_.isDefined).mkString(", ")
}

object Address {
  // TODO: Move field defs to AddressF object?
  import Isdiah._

  def isValidWebsite(s: String): Boolean = {
    import utils.forms.isValidUrl
    // FIXME: This is lame...
    if (!s.trim.startsWith("http://") && s.contains("."))
      isValidUrl("http://" + s)
    else isValidUrl(s)
  }

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
        case url => isValidWebsite(url)
      }))
    )(AddressF.apply)(AddressF.unapply)
  )
}

