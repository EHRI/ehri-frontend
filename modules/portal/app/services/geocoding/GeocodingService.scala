package services.geocoding

import com.google.inject.ImplementedBy
import models.AddressF

import scala.concurrent.Future

case class Point(latitude: BigDecimal, longitude: BigDecimal)

@ImplementedBy(classOf[BingGeocodingService])
trait GeocodingService {
  def geocode(address: AddressF): Future[Option[Point]]
}
