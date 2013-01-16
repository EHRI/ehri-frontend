package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import acl.GlobalPermissionSet
import models.base.Accessor
import defines._
import models.UserProfile
import models.base.AccessibleEntity
import play.api.http.HeaderNames
import play.api.http.ContentTypes
import play.api.libs.json.Json

case class VisibilityDAO[T <: AccessibleEntity](val accessor: UserProfile) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  private val authHeaders: Map[String, String] = headers + (
    AUTH_HEADER_NAME -> accessor.id
  )
  
  def set(user: T, data: List[String]): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, user.id))
    	.withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
        checkError(response).right.map(r => true)
      }
  }
}