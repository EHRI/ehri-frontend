package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.cache.Cache
import backend.{BackendReadable, Visibility, EventHandler, ApiUser}
import play.api.http.Status
import play.api.libs.ws.WSResponse


/**
 * Set visibility on items.
 */
trait RestVisibility extends Visibility with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url: String = enc(baseUrl, "access", id)
    userCall(url)
        .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
        .post("").map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      Cache.remove(id)
      eventHandler.handleUpdate(id)
      r
    }
  }

  private def handler(id: String, response: WSResponse): Boolean = {
    checkError(response)
    Cache.remove(id)
    eventHandler.handleUpdate(id)
    response.status == Status.OK
  }

  def promote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url: String = enc(baseUrl, "promote", id, "up")
    userCall(url).post("").map(handler(id, _))
  }

  def removePromotion(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url: String = enc(baseUrl, "promote", id, "up")
    userCall(url).delete().map(handler(id, _))
  }

  def demote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url: String = enc(baseUrl, "promote", id, "down")
    userCall(url).post("").map(handler(id, _))
  }

  def removeDemotion(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url: String = enc(baseUrl, "promote", id, "down")
    userCall(url).delete().map(handler(id, _))
  }
}