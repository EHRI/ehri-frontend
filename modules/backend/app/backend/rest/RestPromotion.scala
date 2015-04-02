package backend.rest

import scala.concurrent.Future
import play.api.cache.Cache
import backend._
import play.api.libs.ws.WSResponse

/**
 * Manage promotion
 */
trait RestPromotion extends Promotion with RestDAO with RestContext {

  private def requestUrl = s"$baseUrl/promote"

  private def handler[MT: Resource](id: String, response: WSResponse): MT = {
    val item: MT = checkErrorAndParse(response)(Resource[MT].restReads)
    Cache.remove(canonicalUrl(id))
    eventHandler.handleUpdate(id)
    item
  }

  override def promote[MT: Resource](id: String): Future[MT] =
    userCall(enc(requestUrl, id, "up")).post("").map(handler(id, _))

  override def removePromotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(requestUrl, id, "up")).delete().map(handler(id, _))

  override def demote[MT: Resource](id: String): Future[MT] =
    userCall(enc(requestUrl, id, "down")).post("").map(handler(id, _))

  override def removeDemotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(requestUrl, id, "down")).delete().map(handler(id, _))
}