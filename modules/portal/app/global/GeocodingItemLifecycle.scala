package global

import defines.EventType
import javax.inject.{Inject, Singleton}
import models.base.Model
import models.{AddressF, Repository, RepositoryF}
import services.geocoding.GeocodingService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class GeocodingItemLifecycle @Inject()(geocoder: GeocodingService) extends ItemLifecycle {

  private val logger = play.api.Logger(classOf[GeocodingItemLifecycle])

  private def address(repo: RepositoryF): Option[AddressF] =
    repo.descriptions.flatMap(_.addresses.find(_.streetAddress.isDefined)).headOption

  private def geocode(repoData: RepositoryF)(implicit ec: ExecutionContext): Future[RepositoryF] = {
    address(repoData) match {
      case Some(a) =>
        geocoder.geocode(a).map { point =>
          logger.info(s"Geocoding $a (${repoData.id}): $point")
          repoData.copy(latitude = point.map(_.latitude), longitude = point.map(_.longitude))
        }
      case None => Future.successful(repoData.copy(latitude = None, longitude = None))
    }
  }

  override def preSave[MT <: Model](id: Option[String], item: Option[MT], data: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT#T] = {
    (item, data) match {
      // The item has been created...
      case (None, repoData: RepositoryF) =>
        geocode(repoData).map(_.asInstanceOf[MT#T])

      // The item has been updated and the address has changed...
      case (Some(r: Repository), repoData: RepositoryF) if address(r.data) != address(repoData) =>
        geocode(repoData).map(_.asInstanceOf[MT#T])

      // Not a repository...
      case _ => Future.successful(data)
    }
  }

  override def postSave[MT <: Model](id: String, item: MT, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT] = {
    Future.successful(item)
  }
}
