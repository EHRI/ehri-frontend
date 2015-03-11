package models

import defines.EntityType
import models.base.Model
import play.api.libs.json._
import models.json._
import backend.{Entity, BackendWriteable}


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
    (__ \ DATA \ EMAIL).readSeqOrSingle[String] and
    (__ \ DATA \ TELEPHONE).readSeqOrSingle[String] and
    (__ \ DATA \ FAX).readSeqOrSingle[String] and
    (__ \ DATA \ URL).readSeqOrSingle[String]
  )(AddressF.apply _)

  implicit val addressFormat: Format[AddressF] = Format(addressReads,addressWrites)

  implicit object Converter extends BackendWriteable[AddressF] {
    val restFormat: Format[AddressF] = addressFormat
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
  email: Seq[String] = Nil,
  telephone: Seq[String] = Nil,
  fax: Seq[String] = Nil,
  url: Seq[String] = Nil
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

  def concise: String =
    Seq(streetAddress, city, region).flatten.filterNot(_.trim.isEmpty).mkString(", ")

  override def toString =
    Seq(name, contactPerson, streetAddress, city).filter(_.isDefined).mkString(", ")
}

object Address {
  import Isdiah._
  import play.api.data.Form
  import play.api.data.Forms._

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
      EMAIL -> seq(email),
      TELEPHONE -> seq(nonEmptyText),
      FAX -> seq(nonEmptyText),
      URL -> seq(nonEmptyText verifying("error.badUrl",
        url => utils.forms.isValidUrl(url)
      ))
    )(AddressF.apply)(AddressF.unapply)
  )
}

