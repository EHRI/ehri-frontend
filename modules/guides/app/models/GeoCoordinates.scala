package models

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats.doubleFormat

/*
* Represents Geo Coordinates with latitude and longitude
*/
case class GeoCoordinates(
  lat: Double,
  lng: Double,
  distance: Option[Double] = None
)
object GeoCoordinates {

  val form = Form(
    mapping(
      "lat" -> of[Double].verifying("validCoords", lat => lat <= 180 && lat >= -180),
      "lng" -> of[Double].verifying("validCoords", lng => lng <= 180 && lng >= -180),
      "d" -> optional(of[Double].verifying("nonNegative", d => d > 0))
    )(GeoCoordinates.apply)(GeoCoordinates.unapply)
  )
} 
