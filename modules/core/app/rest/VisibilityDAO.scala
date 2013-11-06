package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestReadable
import play.api.libs.json.JsObject
import models.base.AnyModel


/**
 * Set visibility on items.
 */
case class VisibilityDAO()(implicit eventHandler: RestEventHandler) extends RestDAO {

  import Constants._

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  def set[MT](id: String, data: List[String])(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT] = {
    WS.url(enc(requestUrl, id))
        .withQueryString(data.map(ACCESSOR_PARAM -> _): _*)
        .withHeaders(authHeaders.toSeq: _*).post("").map { response =>
      val r = checkErrorAndParse(response)(rd.restReads)
      Cache.remove(id)
      eventHandler.handleUpdate(id)
      r
    }
  }
}