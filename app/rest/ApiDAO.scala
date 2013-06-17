package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.UserProfile
import play.api.libs.ws.Response
import play.api.mvc.Headers

case class ApiDAO(val userProfile: Option[UserProfile]) extends RestDAO {

  def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  def get(urlpart: String, headers: Headers): Future[Response] = {
    WS.url(enc(requestUrl, urlpart))
    	.withHeaders(authHeaders.toSeq: _*).get
  }

  def get(urlpart: String, params: Map[String,Seq[String]] = Map.empty, headers: Headers): Future[Response] = {
    WS.url(enc(requestUrl, urlpart) + "?" + joinQueryString(params))
      .withHeaders(authHeaders.toSeq: _*).get
  }
}