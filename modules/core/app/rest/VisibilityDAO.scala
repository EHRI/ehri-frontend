package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestReadable
import play.api.libs.json.JsObject

/**
 * Set visibility on items.
 * @param userProfile
 */
case class VisibilityDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  def set[MT](id: String, data: List[String])(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, id))
        .withQueryString(data.map(RestPageParams.ACCESSOR_PARAM -> _): _*)
        .withHeaders(authHeaders.toSeq: _*).post("").map { response =>
      checkErrorAndParse(response)(rd.restReads).right.map { r =>
        Cache.remove(id)
        EntityDAO.handleUpdate(response.json.as[JsObject])
        r
      }
    }
  }
}