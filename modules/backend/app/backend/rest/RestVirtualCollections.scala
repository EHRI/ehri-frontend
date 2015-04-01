package backend.rest

import backend._
import defines.EntityType
import play.api.cache.Cache

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestVirtualCollections extends VirtualCollections with RestDAO {

  import backend.rest.Constants._
  val eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  override def addReferences[MT](vcId: String, ids: Seq[String])(implicit rs: BackendResource[MT]): Future[Unit] =
    userCall(enc(baseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).post("").map { _ =>
      eventHandler.handleUpdate(vcId)
      Cache.remove(canonicalUrl(vcId))
    }

  override def deleteReferences[MT](vcId: String, ids: Seq[String])(implicit rs: BackendResource[MT]): Future[Unit] =
    if (ids.isEmpty) Future.successful(())
    else userCall(enc(baseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).delete().map { _ =>
      eventHandler.handleUpdate(vcId)
      Cache.remove(canonicalUrl(vcId))
    }

  override def moveReferences[MT](fromVc: String, toVc: String, ids: Seq[String])(implicit rs: BackendResource[MT]): Future[Unit] =
    if (ids.isEmpty) Future.successful(())
    else userCall(enc(baseUrl, EntityType.VirtualUnit, fromVc, "includes", toVc))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).post("").map { _ =>
      // Update both source and target sets in the index
      Cache.remove(canonicalUrl(fromVc))
      Cache.remove(canonicalUrl(toVc))
      eventHandler.handleUpdate(fromVc)
      eventHandler.handleUpdate(toVc)
    }
}
