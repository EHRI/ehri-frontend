package models

import defines.EntityType
import models.base.Model
import play.api.libs.json._
import models.json._


object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"

  import Entity._
  import Isdiah._
  import play.api.libs.functional.syntax._

  implicit val addressWrites = new Writes[AddressF] {
    def writes(d: AddressF): JsValue = {
      Json.obj(
        ID -> d.id,
        TYPE -> d.isA,
        DATA -> Json.obj(
          ADDRESS_NAME -> d.name,
          CONTACT_PERSON -> d.contactPerson,
          STREET_ADDRESS -> d.streetAddress,
          CITY -> d.city,
          REGION -> d.region,
          POSTAL_CODE -> d.postalCode,
          COUNTRY_CODE -> d.countryCode,
          EMAIL -> d.email,
          TELEPHONE -> d.telephone,
          FAX -> d.fax,
          URL -> d.url
        )
      )
    }
  }

  implicit val addressReads: Reads[AddressF] = (
    (__ \ TYPE).readIfEquals(EntityType.Address) and
    (__ \ ID).readNullable[String] and
    (__ \ DATA \ ADDRESS_NAME).readNullable[String] and
    (__ \ DATA \ CONTACT_PERSON).readNullable[String] and
    (__ \ DATA \ STREET_ADDRESS).readNullable[String] and
    (__ \ DATA \ CITY).readNullable[String] and
    (__ \ DATA \ REGION).readNullable[String] and
    (__ \ DATA \ POSTAL_CODE).readNullable[String] and
    (__ \ DATA \ COUNTRY_CODE).readNullable[String] and
    (__ \ DATA \ EMAIL).readListOrSingle[String] and
    (__ \ DATA \ TELEPHONE).readListOrSingle[String] and
    (__ \ DATA \ FAX).readListOrSingle[String] and
    (__ \ DATA \ URL).readListOrSingle[String]
  )(AddressF.apply _)

  implicit val addressFormat: Format[AddressF] = Format(addressReads,addressWrites)

  implicit object Converter extends RestConvertable[AddressF] with ClientConvertable[AddressF] {
    val restFormat: Format[AddressF] = addressFormat
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

  def toSeq: Seq[String] = Seq(
    streetAddress,
    city,
    region,
    postalCode,
    countryCode.map(views.Helpers.countryCodeToName),
    email.headOption,
    telephone.headOption,
    fax.headOption,
    url.headOption
  ).flatten

  override def toString
      = List(name, contactPerson,streetAddress,city).filter(_.isDefined).mkString(", ")
}

object Address {
  import Isdiah._
  import play.api.data.Form
  import play.api.data.Forms._

  def isValidWebsite(s: String): Boolean = {
    import utils.forms.isValidUrl
    if (!s.trim.startsWith("http://") && s.contains(".")) isValidUrl("http://" + s)
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

