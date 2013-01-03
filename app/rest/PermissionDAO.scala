package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import acl._
import models.base.Accessor
import com.codahale.jerkson.Json
import defines._
import models.UserProfile

object PermissionDAO

case class PermissionDAO[T <: Accessor](val accessor: UserProfile) extends RestDAO {

  import play.api.http.Status._

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  private val authHeaders: Map[String, String] = headers + (
    AUTH_HEADER_NAME -> accessor.id
  )

  def get: Future[Either[RestError, GlobalPermissionSet[UserProfile]]] = {
    WS.url(enc(requestUrl)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => GlobalPermissionSet(accessor, r.json))
    }
  }

  def get(user: T): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => GlobalPermissionSet[T](user, r.json))
      }
  }

  def getItem(id: String): Future[Either[RestError, ItemPermissionSet[UserProfile]]] = {
    WS.url(enc(requestUrl, accessor.id, id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => ItemPermissionSet(accessor, r.json))
      }
  }

  def getItem(user: T, id: String): Future[Either[RestError, ItemPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id, id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => ItemPermissionSet[T](user, r.json))
      }
  }

  def setForScope(user: T, ctype: ContentType.Value, id: String, data: List[String]): Future[Either[RestError, ItemPermissionSet[T]]] = {
    WS.url(enc(requestUrl, id, ctype, user.id))
      .withHeaders(authHeaders.toSeq: _*).post(Json.generate(data)).map { response =>
      checkError(response).right.map(r => ItemPermissionSet[T](user, r.json))
    }
  }

  def set(user: T, data: Map[String, List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id))
      .withHeaders(authHeaders.toSeq: _*).post(Json.generate(data)).map { response =>
        checkError(response).right.map(r => GlobalPermissionSet[T](user, r.json))
      }
  }

  def addGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
      .withHeaders(authHeaders.toSeq: _*).post(Map[String, List[String]]()).map { response =>
        checkError(response).right.map(r => r.status == OK)
      }
  }

  def removeGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
      .withHeaders(authHeaders.toSeq: _*).delete.map { response =>
        checkError(response).right.map(r => r.status == OK)
      }
  }
}