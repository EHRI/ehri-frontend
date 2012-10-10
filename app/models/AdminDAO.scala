package models

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{ WS, Response }
import play.api.libs.json.{ JsArray, JsValue }
import play.api.Play

import com.codahale.jerkson.Json.generate

import com.codahale.jerkson.Json.generate

case class AdminDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/%s/admin".format(host, port, mount)

  private val headers: Seq[(String,String)] = Seq(
      "Content-Type" -> "application/json"
  )
    
  def createNewUserProfile: Future[Either[RestError, Entity]] = {
    WS.url(requestUrl + "/createDefaultUserProfile").withHeaders(headers: _*)
      .post("").map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }
}