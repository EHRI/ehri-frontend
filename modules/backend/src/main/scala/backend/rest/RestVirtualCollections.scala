package backend.rest

import backend._
import defines.EntityType
import scala.concurrent.Future


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait RestVirtualCollections extends VirtualCollections with RestDAO with RestContext {

  import backend.rest.Constants._

  override def addReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] =
    userCall(enc(baseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).post("").map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def deleteReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) Future.successful(())
    else userCall(enc(baseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).delete().map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def moveReferences[MT: Resource](fromVc: String, toVc: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) Future.successful(())
    else userCall(enc(baseUrl, EntityType.VirtualUnit, fromVc, "includes", toVc))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).post("").map { _ =>
      // Update both source and target sets in the index
      cache.remove(canonicalUrl(fromVc))
      cache.remove(canonicalUrl(toVc))
      eventHandler.handleUpdate(fromVc)
      eventHandler.handleUpdate(toVc)
    }
}
