package models

import models.base.Model
import play.api.data.Forms._
import play.api.data.Form

/*
*
* Represents Geo Coordinates with latitude and longitude
*
*/
object GeoCoordinates {

  val form = Form(
    tuple(
      "lat" -> bigDecimal,
      "lng" -> bigDecimal
    ) verifying("validCoords", f => f match {
      case (lat, lng) =>
        lat <= 180 && lat >= -180 && lng <= 180 && lng >= -180
    })
  )
} 

case class GeoCoordinates(
  lat: BigDecimal,
  lng: BigDecimal
) {
  override def toString = List(lat, lng).mkString(",")
}