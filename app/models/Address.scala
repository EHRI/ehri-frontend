package models

import defines.EntityType
import models.base.{Formable, AccessibleEntity}
import play.api.libs.json.Json


case class Address(val e: Entity) extends AccessibleEntity with Formable[AddressF] {
  import json.AddressFormat._
  lazy val formable: AddressF = Json.toJson(e).as[AddressF]
  lazy val formableOpt: Option[AddressF] = Json.toJson(e).asOpt[AddressF]
}

object AddressF {
  val UNNAMED_ADDRESS = "Unnamed Address"
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
}

