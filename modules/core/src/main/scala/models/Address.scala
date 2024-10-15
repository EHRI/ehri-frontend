package models

import models.json._
import play.api.libs.json._

import java.util.Locale


object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"

  import Entity._
  import Isdiah._
  import play.api.libs.functional.syntax._

  implicit lazy val _format: Format[AddressF] = (
    (__ \ TYPE).formatIfEquals(EntityType.Address) and
    (__ \ ID).formatNullable[String] and
    (__ \ DATA \ ADDRESS_NAME).formatNullable[String] and
    (__ \ DATA \ CONTACT_PERSON).formatNullable[String] and
    (__ \ DATA \ STREET_ADDRESS).formatNullable[String] and
    (__ \ DATA \ CITY).formatNullable[String] and
    (__ \ DATA \ REGION).formatNullable[String] and
    (__ \ DATA \ POSTAL_CODE).formatNullable[String] and
    (__ \ DATA \ COUNTRY_CODE).formatNullable[String] and
    (__ \ DATA \ EMAIL).formatSeqOrSingle[String] and
    (__ \ DATA \ TELEPHONE).formatSeqOrSingle[String] and
    (__ \ DATA \ FAX).formatSeqOrSingle[String] and
    (__ \ DATA \ URL).formatSeqOrSingle[String]
  )(AddressF.apply, unlift(AddressF.unapply))

  implicit object Converter extends Writable[AddressF] {
    val _format: Format[AddressF] = AddressF._format
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
  ) extends ModelData {

  /**
    * Returns a concise representation of the address, suitable for display in a list.
    */
  def concise: String =
    Seq(streetAddress, city, region).flatten.filterNot(_.trim.isEmpty).mkString(", ")

  /**
    * Returns a postal address, suitable for display in a single block.
    */
  def postalAddress: String =
    Seq(streetAddress, city, region, postalCode, countryCode).flatten.filterNot(_.trim.isEmpty).mkString("\n")

  /**
    * Returns the ISO 3166-1 alpha-3 code for the country, if available.
    */
  def countryCode3: Option[String] =
    countryCode.flatMap(c => Locale.getAvailableLocales.find(_.getCountry == c).map(_.getISO3Country))

  override def toString: String =
    Seq(name, contactPerson, streetAddress, city, region, countryCode).filter(_.isDefined).mkString(", ")
}

object Address {
  import Isdiah._
  import play.api.data.Form
  import play.api.data.Forms._

  val form: Form[AddressF] = Form(
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
      URL -> seq(nonEmptyText.verifying("error.badUrl",
        url => forms.isValidUrl(url)
      ))
    )(AddressF.apply)(AddressF.unapply)
  )
}

