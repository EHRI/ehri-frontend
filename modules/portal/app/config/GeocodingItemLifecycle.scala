package config

import defines.EventType
import javax.inject.{Inject, Singleton}
import models.base.Model
import models.{AddressF, RepositoryF}
import services.geocoding.GeocodingService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
case class GeocodingItemLifecycle @Inject()(geocoder: GeocodingService) extends ItemLifecycle {

  private val logger = play.api.Logger(classOf[GeocodingItemLifecycle])

  private def address(repo: RepositoryF): Option[AddressF] =
    repo.descriptions.flatMap(_.addresses.find(_.streetAddress.isDefined)).headOption

  private def geocode(id: Option[String], repoData: RepositoryF)(implicit ec: ExecutionContext): Future[RepositoryF] = {
    address(repoData) match {
      case Some(a) =>
        geocoder.geocode(a).map { point =>
          logger.info(s"Geocoding $a (${id.getOrElse("???")}): ${point.map(_.toString).getOrElse("???")}")
          repoData.copy(latitude = point.map(_.latitude), longitude = point.map(_.longitude))
        }
      case None => Future.successful(repoData.copy(latitude = None, longitude = None))
    }
  }

  override def preSave[MT <: Model](id: Option[String], item: Option[MT], data: MT#T, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT#T] = {
    data match {
      // A repository has been created or updated...
      case repoData: RepositoryF => geocode(id, repoData).map(_.asInstanceOf[MT#T])

      // Not a repository...
      case _ => Future.successful(data)
    }
  }

  override def postSave[MT <: Model](id: String, item: MT, event: EventType.Value)(implicit ec: ExecutionContext): Future[MT] = {
    Future.successful(item)
  }
}
