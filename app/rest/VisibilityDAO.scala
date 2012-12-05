package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity
import models.UserProfile
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.{PermissionSet, Group}
import models.base.Accessor
import com.codahale.jerkson.Json
import defines._
import models.UserProfileRepr
import models.base.AccessibleEntity

case class VisibilityDAO[T <: AccessibleEntity](val accessor: UserProfileRepr) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/access".format(host, port, mount)

  private val headers: Map[String, String] = Map(
    "Content-Type" -> "application/json",
    "Authorization" -> accessor.identifier
  )
  
  def set(user: T, data: List[String]): Future[Either[RestError, Boolean]] = {
    WS.url(enc("%s/%s".format(requestUrl, user.id)))
    	.withHeaders(headers.toSeq: _*).post(Json.generate(data)).map { response =>
        checkError(response).right.map(r => true)
      }
  }
}