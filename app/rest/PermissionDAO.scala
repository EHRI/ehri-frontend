package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import acl._
import models.base.Accessor
import defines._
import models.{Entity, UserProfile}
import play.api.libs.json.{Json,JsValue,JsArray}

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

  // FIXME: Hard-coded limit
  def list(user: T, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "list/%s?offset=%d&limit=%d".format(user.id, (page-1)*limit, limit)))

  def listForItem(id: String, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "listForItem/%s?offset=%d&limit=%d".format(id, (page-1)*limit, limit)))

  def listForScope(id: String, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "listForScope/%s?offset=%d&limit=%d".format(id, (page-1)*limit, limit)))

  private def listWithUrl(url: String): Future[Either[RestError, Page[models.PermissionGrant]]] = {
    import Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads
    WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[models.Entity]].fold(
          valid = { page =>
            Page(page.total, page.offset, page.limit, page.list.map(models.PermissionGrant(_)))
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: %s\n%s".format(e, r.json))
          }
        )
      }
    }
  }

  def get(user: T): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => GlobalPermissionSet[T](user, r.json))
      }
  }

  def set(user: T, data: Map[String, List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id))
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map(r => GlobalPermissionSet[T](user, r.json))
    }
  }

  def getItem(contentType: ContentType.Value, id: String): Future[Either[RestError, ItemPermissionSet[UserProfile]]] = {
    WS.url(enc(requestUrl, accessor.id, id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => ItemPermissionSet[UserProfile](accessor, contentType, r.json))
    }
  }

  def getItem(user: T, contentType: ContentType.Value, id: String): Future[Either[RestError, ItemPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id, id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map(r => ItemPermissionSet[T](user, contentType, r.json))
      }
  }

  def setItem(user: T, contentType: ContentType.Value, id: String, data: List[String]): Future[Either[RestError, ItemPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id, id))
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map(r => ItemPermissionSet[T](user, contentType, r.json))
    }
  }

  def getScope(id: String): Future[Either[RestError, GlobalPermissionSet[UserProfile]]] = {
    WS.url(enc(requestUrl, accessor.id, "scope", id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => GlobalPermissionSet[UserProfile](accessor, r.json))
    }
  }

  def getScope(user: T, id: String): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id, "scope", id))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => GlobalPermissionSet[T](user, r.json))
    }
  }

  def setScope(user: T, id: String, data: Map[String,List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    WS.url(enc(requestUrl, user.id, "scope", id))
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
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