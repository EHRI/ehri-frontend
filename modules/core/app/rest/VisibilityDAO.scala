package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.Play.current
import play.api.cache.Cache
import models.json.RestReadable


case class VisibilityDAO(userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  def set[MT](id: String, data: List[String])(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val params = "?" + data.map(p => "%s=%s".format(RestPageParams.ACCESSOR_PARAM, p)).mkString("&")
    WS.url(enc(requestUrl, id, params)).withHeaders(authHeaders.toSeq: _*).post("").map { response =>
        checkError(response).right.map { r =>
          Cache.remove(id)
          //EntityDAO.handleUpdate(EntityDAO.jsonToEntity(r.json))
          r.json.as[MT](rd.restReads)
        }
      }
  }
}