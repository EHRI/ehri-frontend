package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.Play.current
import play.api.cache.Cache
import backend.{BackendReadable, Visibility, EventHandler, ApiUser}
import play.api.http.Status
import java.lang.Readable


/**
 * Set visibility on items.
 */
trait RestVisibility extends Visibility with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    userCall(enc(requestUrl, "access", id))
        .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
        .post("").map { response =>
      val r = checkErrorAndParse(response)(rd.restReads)
      Cache.remove(id)
      eventHandler.handleUpdate(id)
      r
    }
  }

  def promote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(requestUrl, "promote", id)).post("").map { response =>
      checkError(response)
      Cache.remove(id)
      response.status == Status.OK
    }
  }

  def demote(id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(requestUrl, "promote", id)).delete().map { response =>
      checkError(response)
      Cache.remove(id)
      response.status == Status.OK
    }
  }
}

case class VisibilityDAO(eventHandler: EventHandler) extends RestVisibility