package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import acl._
import models.base.Accessor
import defines._
import models.{Entity, UserProfile}
import play.api.libs.json.Json
import play.api.Play.current
import play.api.cache.Cache


case class PermissionDAO[T <: Accessor](userProfile: Option[UserProfile]) extends RestDAO {

  import play.api.http.Status._

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  def get: Future[Either[RestError, GlobalPermissionSet[UserProfile]]] = {
    userProfile.map { up =>
        val url = enc(requestUrl, up.id)
        WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
          checkError(response).right.map { r =>
            val globalPerms = GlobalPermissionSet(up, r.json)
            Cache.set(url, globalPerms, cacheTime)
            globalPerms
          }
        }
      } getOrElse {
      // If we don't have a user we can't get our own profile, so just return PermissionDenied
        Future.successful(Left(PermissionDenied()))
    }
  }

  // FIXME: Hard-coded limit
  def list(user: T, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "page/%s?offset=%d&limit=%d".format(user.id, (page-1)*limit, limit)))

  def listForItem(id: String, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForItem/%s?offset=%d&limit=%d".format(id, (page-1)*limit, limit)))

  def listForScope(id: String, page: Int, limit: Int): Future[Either[RestError, Page[models.PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForScope/%s?offset=%d&limit=%d".format(id, (page-1)*limit, limit)))

  private def listWithUrl(url: String): Future[Either[RestError, Page[models.PermissionGrant]]] = {
    import Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads
    WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[models.Entity]].fold(
          valid = { page =>
            Page(page.total, page.offset, page.limit, page.items.map(models.PermissionGrant(_)))
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: %s\n%s".format(e, r.json))
          }
        )
      }
    }
  }

  def get(user: T): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val gperms = GlobalPermissionSet[T](user, r.json)
          Cache.set(url, gperms, cacheTime)
          gperms
        }
      }
  }

  def set(user: T, data: Map[String, List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map { r =>
        val gperms = GlobalPermissionSet[T](user, r.json)
        Cache.set(url, gperms, cacheTime)
        gperms
      }
    }
  }

  def getItem(contentType: ContentType.Value, id: String): Future[Either[RestError, ItemPermissionSet[UserProfile]]] = {
    userProfile.map { up =>
      val url = enc(requestUrl, up.id, id)
      val cached = Cache.getAs[ItemPermissionSet[UserProfile]](url)
      if (cached.isDefined) Future.successful(Right(cached.get))
      else {
        WS.url(url)
          .withHeaders(authHeaders.toSeq: _*).get.map { response =>
          checkError(response).right.map { r =>
            val iperms = ItemPermissionSet[UserProfile](up, contentType, r.json)
            Cache.set(url, iperms, cacheTime)
            iperms
          }
        }
      }
    } getOrElse {
      Future.successful(Left(PermissionDenied()))
    }
  }

  def getItem(user: T, contentType: ContentType.Value, id: String): Future[Either[RestError, ItemPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, id)
    val cached = Cache.getAs[ItemPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(Right(cached.get))
    else {
      WS.url(url)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val iperms = ItemPermissionSet[T](user, contentType, r.json)
          Cache.set(url, iperms, cacheTime)
          iperms
        }
      }
    }
  }

  def setItem(user: T, contentType: ContentType.Value, id: String, data: List[String]): Future[Either[RestError, ItemPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, id)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map { r =>
        val iperms = ItemPermissionSet[T](user, contentType, r.json)
        Cache.set(url, iperms, cacheTime)
        iperms
      }
    }
  }

  def getScope(id: String): Future[Either[RestError, GlobalPermissionSet[UserProfile]]] = {
    userProfile.map { up =>
      val url = enc(requestUrl, up.id, "scope", id)
      WS.url(url)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val sperms = GlobalPermissionSet[UserProfile](up, r.json)
          Cache.set(url, sperms, cacheTime)
          sperms
        }
      }
    } getOrElse {
      Future.successful(Left(PermissionDenied()))
    }
  }

  def getScope(user: T, id: String): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        val sperms = GlobalPermissionSet[T](user, r.json)
        Cache.set(url, sperms, cacheTime)
        sperms
      }
    }
  }

  def setScope(user: T, id: String, data: Map[String,List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map { r =>
        val sperms = GlobalPermissionSet[T](user, r.json)
        Cache.set(url, sperms, cacheTime)
        sperms
      }
    }
  }

  def addGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
      .withHeaders(authHeaders.toSeq: _*).post(Map[String, List[String]]()).map { response =>
        checkError(response).right.map { r =>
          Cache.remove(userId)
          r.status == OK
        }
      }
  }

  def removeGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
      .withHeaders(authHeaders.toSeq: _*).delete.map { response =>
        checkError(response).right.map { r =>
          Cache.remove(userId)
          r.status == OK
        }
      }
  }
}