package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity
import play.api.http.HeaderNames
import play.api.http.ContentTypes

case class AdminDAO() extends RestDAO {

  // Import implicit entity reader  
  import models.EntityReader.entityReads
  
  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  def createNewUserProfile: Future[Either[RestError, Entity]] = {

    WS.url(requestUrl + "/createDefaultUserProfile").withHeaders(headers.toSeq: _*)
      .post("").map { response =>
        checkError(response).right.map(r => r.json.as[Entity])
      }
  }
}