package models

import play.api.data.Form
import play.api.data.Forms._

/*
* Represents Geo Coordinates with latitude and longitude
*/
case class GeoCoordinates(
  lat: BigDecimal,
  lng: BigDecimal
) {
  override def toString = s"$lat,$lng"
}
object GeoCoordinates {

  val form = Form(
    mapping(
      "lat" -> bigDecimal.verifying("validCoords", lat => lat <= 180 && lat >= -180),
      "lng" -> bigDecimal.verifying("validCoords", lng => lng <= 180 && lng >= -180)
    )(GeoCoordinates.apply)(GeoCoordinates.unapply)
  )
} 
