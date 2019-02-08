package services.geocoding

import javax.inject.Inject
import models.AddressF
import play.api.Configuration
import play.api.libs.json.{JsError, JsSuccess}
import play.api.libs.ws.WSClient

import scala.concurrent.{ExecutionContext, Future}


case class BingGeocodingService @Inject()(ws: WSClient, config: Configuration)(implicit executionContext: ExecutionContext) extends GeocodingService {

  private val logger = play.api.Logger(classOf[BingGeocodingService])
  private val bingKey = config.getOptional[String]("bing.key")

  case class Response(point: Point, confidence: String)
  object Response {
    import play.api.libs.functional.syntax._
    import play.api.libs.json._

    implicit def pointReads: Reads[Point] = (
      (__ \ "point" \ "coordinates" \ 0).read[BigDecimal] and
      (__ \ "point" \ "coordinates" \ 1).read[BigDecimal]
    )(Point.apply _)

    implicit def reads: Reads[Response] = (
      (__ \ "resourceSets" \ 0 \ "resources" \ 0).read[Point] and
      (__ \ "resourceSets" \ 0 \ "resources" \ 0 \ "confidence").read[String]
    )(Response.apply _)
  }

  override def geocode(address: AddressF): Future[Option[Point]] = {

    val params = Seq(
      "countryRegion" -> address.countryCode,
      "userRegion" -> address.countryCode,
      "adminDistrict" -> address.region,
      "postalCode" -> address.postalCode,
      "addressLine" -> address.streetAddress,
      "locality" -> address.city,
      "maxResults" -> Some("2"),
      "includeNeighbourhood" -> Some("false")
    ).collect { case (k, v) => k -> v.getOrElse("-") }

    logger.debug(s"Geocoding address: $params")
    ws.url("https://dev.virtualearth.net/REST/v1/Locations")
        .withQueryStringParameters(params: _*)
        .addQueryStringParameters("key" -> bingKey.getOrElse("-"))
        .get().map { r =>
      logger.debug(s"Bing response: ${r.body}")
      r.json.validate[Response] match {
        case JsSuccess(Response(point, "High"), _) =>
          logger.debug(s"Geocode for $address: $point")
          Some(point)
        case JsSuccess(Response(point, confidence), _) =>
          logger.warn(s"Geocode confidence ($confidence) not high for: $address")
          None
        case JsError(e) =>
          logger.error(s"Unable to parse Geocode response for $address: $e")
          None
      }
    } recover {
      case e: Throwable =>
        logger.error("Bing Geocoding error", e)
        None
    }
  }
}
