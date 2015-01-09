package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.cache.Cache
import backend._
import play.api.http.Status
import backend.ApiUser
import play.api.libs.ws.WSResponse

/**
 * Manage promotion
 */
trait RestPromotion extends Promotion with RestDAO {

  this: RestGeneric =>

  val eventHandler: EventHandler

  private def requestUrl = s"$baseUrl/promote"

  private def handler[MT](id: String, response: WSResponse)(implicit rd: BackendReadable[MT], rs: BackendResource[MT]): MT = {
    val item: MT = checkErrorAndParse(response)(rd.restReads)
    Cache.remove(canonicalUrl(id))
    eventHandler.handleUpdate(id)
    item
  }

  def promote[MT](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[MT], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(requestUrl, id, "up")
    userCall(url).post("").map(handler(id, _))
  }

  def removePromotion[MT](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[MT], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(requestUrl, id, "up")
    userCall(url).delete().map(handler(id, _))
  }

  def demote[MT](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[MT], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(requestUrl, id, "down")
    userCall(url).post("").map(handler(id, _))
  }

  def removeDemotion[MT](id: String)(implicit apiUser: ApiUser, rd: BackendReadable[MT], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(requestUrl, id, "down")
    userCall(url).delete().map(handler(id, _))
  }
}