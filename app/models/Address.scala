package models

import defines.EntityType
import models.base.{Formable, AccessibleEntity}
import play.api.libs.json.Json


object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"

  implicit val addressFormat = json.AddressFormat.addressFormat
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
  email: List[String] = Nil,
  telephone: List[String] = Nil,
  fax: List[String] = Nil,
  url: List[String] = Nil
) {
  val isA = EntityType.Address

  override def toString
      = List(name, contactPerson,streetAddress,city).filter(_.isDefined).mkString(", ")
}


case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {
  lazy val formable: AddressF = Json.toJson(e).as[AddressF]
  lazy val formableOpt: Option[AddressF] = Json.toJson(e).asOpt[AddressF]
}
