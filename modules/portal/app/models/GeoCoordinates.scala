package models

import models.base.Model
import play.api.data.Forms._
import play.api.data.Form

/*
*
* Represents Geo Coordinates with latitude and longitude
*
*/

case class GeoCoordinates(
  lat: BigDecimal,
  lng: BigDecimal
) {
  override def toString = List(lat, lng).mkString(",")
}
object GeoCoordinates {

  val form = Form(
    mapping(
      "lat" -> bigDecimal.verifying("validCoords", f => f match {
        case lat =>
          lat <= 180 && lat >= -180
      }),
      "lng" -> bigDecimal.verifying("validCoords", f => f match {
        case lng =>
          lng <= 180 && lng >= -180
      })
    )(GeoCoordinates.apply)(GeoCoordinates.unapply)
  )
} 
