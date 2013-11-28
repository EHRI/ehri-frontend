package backend.rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestReadable
import backend.{Visibility, EventHandler, ApiUser}


/**
 * Set visibility on items.
 */
trait RestVisibility extends Visibility with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  def setVisibility[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id))
        .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
        .withHeaders(authHeaders.toSeq: _*).post("").map { response =>
      val r = checkErrorAndParse(response)(rd.restReads)
      Cache.remove(id)
      eventHandler.handleUpdate(id)
      r
    }
  }
}

case class VisibilityDAO(eventHandler: EventHandler) extends RestVisibility