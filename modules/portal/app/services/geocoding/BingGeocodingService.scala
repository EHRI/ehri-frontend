package services.geocoding

import javax.inject.Inject
import models.AddressF
import play.api.Configuration
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}


case class BingGeocodingService @Inject()(ws: WSClient, config: Configuration)(implicit executionContext: ExecutionContext) extends GeocodingService {

  private val logger = play.api.Logger(classOf[BingGeocodingService])
  private val bingKey = config.getOptional[String]("bing.key")

  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  private implicit def reads: Reads[Point] = (
    (__ \ "resourceSets" \ 0 \ "resources" \ 0 \ "point" \ "coordinates" \ 0).read[BigDecimal] and
    (__ \ "resourceSets" \ 0 \ "resources" \ 0 \ "point" \ "coordinates" \ 1).read[BigDecimal]
  )(Point.apply _)


  override def geocode(address: AddressF): Future[Option[Point]] = {

    val params = Seq(
      "countryRegion" -> address.countryCode,
      "adminDistrict" -> address.region,
      "postalCode" -> address.postalCode,
      "addressLine" -> address.streetAddress,
      "locality" -> address.city,
      "maxResults" -> Some("2"),
      "key" -> bingKey,
      "includeNeighbourhood" -> Some("false")
    ).collect { case (k, v) => k -> v.getOrElse("-") }

    logger.debug(s"Geocoding address: $address")
    ws.url("https://dev.virtualearth.net/REST/v1/Locations")
        .withQueryStringParameters(params: _*).get().map { r =>
      logger.debug(s"Bing response: ${Json.prettyPrint(r.json)}")
      r.json.validate[Point] match {
        case JsSuccess(point, _) =>
          logger.debug(s"Geocode for $address: $point")
          Some(point)
        case JsError(e) =>
          logger.warn(s"Unable to parse Geocode response: $e")
          None
      }
    } recover {
      case e: Throwable =>
        logger.error("Bing Geocoding error", e)
        None
    }
  }
}
