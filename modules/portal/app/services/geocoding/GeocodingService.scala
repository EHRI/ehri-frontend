package services.geocoding

import models.AddressF

import scala.concurrent.Future

case class Point(latitude: BigDecimal, longitude: BigDecimal)

trait GeocodingService {
  def geocode(address: AddressF): Future[Option[Point]]
}
