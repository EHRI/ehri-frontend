package backend.rest

import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import play.api.mvc.Headers
import backend.ApiUser

case class ApiDAO() extends RestDAO {

  def get(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[WSResponse] = {
    userCall(enc(baseUrl, urlpart) + (if(!params.isEmpty) "?" + joinQueryString(params) else "")).get()
  }
}