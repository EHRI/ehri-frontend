package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity


case class AdminDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile: Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, "createDefaultUserProfile")).withHeaders(headers.toSeq: _*)
      .post("").map { response =>
        checkError(response).right.map(r => EntityDAO.jsonToEntity(r.json))
      }
  }
}