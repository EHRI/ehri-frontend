package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile

case class VisibilityDAO(val accessor: UserProfile) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  private val authHeaders: Map[String, String] = headers + (
    AUTH_HEADER_NAME -> accessor.id
  )
  
  def set(id: String, data: List[String]): Future[Either[RestError, Boolean]] = {
    val params = "?" + data.map(p => "%s=%s".format(EntityDAO.ACCESSOR_PARAM, p)).mkString("&")
    WS.url(enc(requestUrl, id, params)).withHeaders(authHeaders.toSeq: _*).post("").map { response =>
        checkError(response).right.map(r => true)
      }
  }
}