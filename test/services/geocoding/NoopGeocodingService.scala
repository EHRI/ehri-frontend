package services.geocoding

import models.AddressF
import play.api.Logger

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
case class NoopGeocodingService @Inject()() extends GeocodingService {
  private val logger = Logger(classOf[NoopGeocodingService])
  override def geocode(address: AddressF): Future[Option[Point]] = {
    logger.info(s"Geocoding disabled: $address")
    Future.successful(None)
  }
}
