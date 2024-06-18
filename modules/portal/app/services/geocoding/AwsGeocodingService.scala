package services.geocoding

import models.AddressF
import software.amazon.awssdk.auth.credentials.{AwsCredentials, AwsCredentialsProvider, StaticCredentialsProvider}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.regions.providers.AwsRegionProvider
import software.amazon.awssdk.services.location.LocationClient
import software.amazon.awssdk.services.location.model.SearchPlaceIndexForTextRequest

import javax.inject.Inject
import scala.jdk.CollectionConverters._
import scala.concurrent.{ExecutionContext, Future}


object AwsGeocodingService {
  def apply(config: com.typesafe.config.Config)(implicit ec: ExecutionContext): AwsGeocodingService = {

    val credentials = StaticCredentialsProvider.create(new AwsCredentials {
      override def accessKeyId(): String = config.getString("config.aws.credentials.access-key-id")
      override def secretAccessKey(): String = config.getString("config.aws.credentials.secret-access-key")
    })
    val region: AwsRegionProvider = new AwsRegionProvider {
      override def getRegion: Region = Region.of(config.getString("config.aws.region.default-region"))
    }

    new AwsGeocodingService(
      credentials,
      region,
      config.getString("config.aws.index-name")
    )
  }
}

case class AwsGeocodingService @Inject()(credentials: AwsCredentialsProvider, region: AwsRegionProvider, indexName: String)(implicit executionContext: ExecutionContext) extends GeocodingService {

  private val logger = play.api.Logger(classOf[AwsGeocodingService])

  override def geocode(address: AddressF): Future[Option[Point]] = Future {
    if (address.postalAddress.trim.nonEmpty && address.countryCode3.nonEmpty) {
      try runLocationGeocode(address) catch {
        case e: Exception =>
          logger.error(s"Error geocoding address: $address", e)
          None
      }
    } else {
      None
    }
  }

  private def runLocationGeocode(address: AddressF): Option[Point] = {
    val client: LocationClient = LocationClient
      .builder()
      .credentialsProvider(credentials)
      .region(region.getRegion)
      .build()

    val request = SearchPlaceIndexForTextRequest.builder()
      .indexName(indexName)
      .text(address.postalAddress)
      .filterCountries(address.countryCode3.toList: _*)
      .maxResults(1)
      .build()

    client.searchPlaceIndexForText(request)
      .results()
      .asScala
      .headOption
      .map { result =>
        logger.debug(s"Geocoding result: $result, point: ${result.place().geometry().point()}")
        val lonLat = result.place().geometry().point()
        Point(
          BigDecimal(lonLat.get(1)),
          BigDecimal(lonLat.get(0))
        )
      }
  }
}
