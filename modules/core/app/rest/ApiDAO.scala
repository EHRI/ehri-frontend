package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.Response
import play.api.mvc.Headers

case class ApiDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  def get(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[Response] = {
    userCall(enc(requestUrl, urlpart) + (if(!params.isEmpty) "?" + joinQueryString(params) else "")).get
  }
}