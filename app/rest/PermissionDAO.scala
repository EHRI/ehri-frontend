package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.Entity
import models.UserProfile
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import models.PermissionSet

case class PermissionDAO(val userProfile: UserProfile) extends RestDAO {

  def requestUrl = "http://%s:%d/%s/permission".format(host, port, mount)

  private val headers: Map[String, String] = Map(
    "Content-Type" -> "application/json",
    "Authorization" -> userProfile.identifier
  )
  
  def getUserPermissions: Future[Either[RestError, PermissionSet]] = {
    WS.url(requestUrl).withHeaders(headers.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => PermissionSet(userProfile, r.json))
      }
  }
}