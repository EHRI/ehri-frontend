package models

import defines.EntityType
import models.base.Model
import play.api.libs.json.{Format, Json}
import models.json.{ClientConvertable, RestConvertable}


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

