package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity

case class AdminDAO() extends RestDAO {

  // Import implicit entity reader  
  import models.EntityReader.entityReads
  
  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  private val headers: Seq[(String,String)] = Seq(
      "Content-Type" -> "application/json"
  )

  def createNewUserProfile: Future[Either[RestError, Entity]] = {

    WS.url(requestUrl + "/createDefaultUserProfile").withHeaders(headers: _*)
      .post("").map { response =>
        checkError(response).right.map(r => r.json.as[Entity])
      }
  }
}