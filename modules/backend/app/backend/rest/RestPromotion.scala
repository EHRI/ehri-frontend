package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.cache.Cache
import backend._
import backend.ApiUser
import play.api.libs.ws.WSResponse

/**
 * Manage promotion
 */
trait RestPromotion extends Promotion with RestDAO {

  val eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  private def requestUrl = s"$baseUrl/promote"

  private def handler[MT](id: String, response: WSResponse)(implicit rs: BackendResource[MT]): MT = {
    val item: MT = checkErrorAndParse(response)(rs.restReads)
    Cache.remove(canonicalUrl(id))
    eventHandler.handleUpdate(id)
    item
  }

  override def promote[MT](id: String)(implicit  rs: BackendResource[MT]): Future[MT] =
    userCall(enc(requestUrl, id, "up")).post("").map(handler(id, _))

  override def removePromotion[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT] =
    userCall(enc(requestUrl, id, "up")).delete().map(handler(id, _))

  override def demote[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT] =
    userCall(enc(requestUrl, id, "down")).post("").map(handler(id, _))

  override def removeDemotion[MT](id: String)(implicit rs: BackendResource[MT]): Future[MT] =
    userCall(enc(requestUrl, id, "down")).delete().map(handler(id, _))
}