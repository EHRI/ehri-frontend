package models

import defines.EntityType
import models.base.{Formable, AccessibleEntity}
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}


object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"

  implicit val addressFormat: Format[AddressF] = json.AddressFormat.restFormat

  implicit object Converter extends RestConvertable[AddressF] with ClientConvertable[AddressF] {
    val restFormat = models.json.rest.addressFormat
    val clientFormat = models.json.client.addressFormat
  }
}


case class AddressF(
  id: Option[String],
  name: Option[String],
  contactPerson: Option[String] = None,
  streetAddress: Option[String] = None,
  city: Option[String] = None,
  region: Option[String] = None,
  postalCode: Option[String] = None,
  countryCode: Option[String] = None,
  email: Option[String] = None,
  telephone: Option[List[String]] = None,
  fax: Option[String] = None,
  url: Option[String] = None
  ) {
  val isA = EntityType.Address

  override def toString
      = List(name, contactPerson,streetAddress,city).filter(_.isDefined).mkString(", ")
}


case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {
  lazy val formable: AddressF = Json.toJson(e).as[AddressF]
  lazy val formableOpt: Option[AddressF] = Json.toJson(e).asOpt[AddressF]
}
