package backend.rest

import play.api.cache.CacheApi

import scala.concurrent.Future
import play.api.libs.ws.WSResponse
import play.api.mvc.Headers
import backend.ApiUser

case class ApiDAO()(implicit val cache: CacheApi, val app: play.api.Application) extends RestDAO {

  def get(urlpart: String, headers: Headers, params: Map[String,Seq[String]] = Map.empty)(implicit apiUser: ApiUser): Future[WSResponse] = {
    userCall(enc(baseUrl, urlpart) + (if(params.nonEmpty) "?" + joinQueryString(params) else "")).get()
  }
}