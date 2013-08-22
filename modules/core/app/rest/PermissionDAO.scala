package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import acl._
import models.base.Accessor
import defines._
import models.{PermissionGrant, UserProfile}
import play.api.libs.json.Json
import play.api.Play.current
import play.api.cache.Cache
import play.api.Logger
import utils.ListParams
import utils.ListParams


case class PermissionDAO[T <: Accessor](userProfile: Option[UserProfile]) extends RestDAO {

  import Constants._
  import play.api.http.Status._

  implicit val permissionGrantMetaReads = PermissionGrant.Converter.restReads
  implicit val pageReads = Page.pageReads(permissionGrantMetaReads)

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  def get: Future[Either[RestError, GlobalPermissionSet[UserProfile]]] = {
    userProfile.map { up =>
      val url = enc(requestUrl, up.id)
      val cached = Cache.getAs[GlobalPermissionSet[UserProfile]](url)
      if (cached.isDefined) {
        Future.successful(Right(cached.get))
      } else {
        Logger.logger.debug("Fetch perms: {}", url)
        WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
          checkError(response).right.map { r =>
            val globalPerms = GlobalPermissionSet(up, r.json)
            // NB: We cache permissions for a shorter time since they are effectively
            // impossible to correctly invalidate when inherited permissions change.
            // Hence we just live with some uncertainty here.
            Cache.set(url, globalPerms, cacheTime / 2)
            globalPerms
          }
        }
      }
    } getOrElse {
    // If we don't have a user we can't get our own profile, so just return PermissionDenied
      Future.successful(Left(PermissionDenied()))
    }
  }

  def list(user: T, params: ListParams): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "page", user.id), params)

  def listForItem(id: String, params: ListParams): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForItem", id), params)

  def listForScope(id: String, params: ListParams): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForScope", id), params)

  private def listWithUrl(url: String, params: ListParams): Future[Either[RestError, Page[PermissionGrant]]] = {
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[PermissionGrant]](response)
    }
  }

  def get(user: T): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(Right(cached.get))
    else {
      WS.url(url)
          .withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val gperms = GlobalPermissionSet[T](user, r.json)
          Cache.set(url, gperms, cacheTime)
          gperms
        }
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

  def getItem(contentType: ContentTypes.Value, id: String): Future[Either[RestError, ItemPermissionSet[UserProfile]]] = {
    userProfile.map { up =>
      val url = enc(requestUrl, up.id, id)
      val cached = Cache.getAs[ItemPermissionSet[UserProfile]](url)
      if (cached.isDefined) Future.successful(Right(cached.get))
      else {
        Logger.logger.debug("Fetch item perms: {}", url)
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

  def getItem(user: T, contentType: ContentTypes.Value, id: String): Future[Either[RestError, ItemPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, id)
    val cached = Cache.getAs[ItemPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(Right(cached.get))
    else {
      Logger.logger.debug("Fetch item perms: {}", url)
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val iperms = ItemPermissionSet[T](user, contentType, r.json)
          Cache.set(url, iperms, cacheTime)
          iperms
        }
      }
    }
  }

  def setItem(user: T, contentType: ContentTypes.Value, id: String, data: List[String]): Future[Either[RestError, ItemPermissionSet[T]]] = {
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
    // FIXME: WHOA - might not be able to invalidate this cache properly
    // other than just waiting it out, since, like global perms, they can
    // be inherited.
    userProfile.map { up =>
      val url = enc(requestUrl, up.id, "scope", id)
      var cached = Cache.getAs[GlobalPermissionSet[UserProfile]](url)
      if (cached.isDefined) Future.successful(Right(cached.get))
      else {
        Logger.logger.debug("Fetch scoped perms: {}", url)
        WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
          checkError(response).right.map { r =>
            val sperms = GlobalPermissionSet[UserProfile](up, r.json)
            Cache.set(url, sperms, cacheTime)
            sperms
          }
        }
      }
    } getOrElse {
      Future.successful(Left(PermissionDenied()))
    }
  }

  def getScope(user: T, id: String): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(Right(cached.get))
    else {
      Logger.logger.debug("Fetch scoped perms: {}", url)
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val sperms = GlobalPermissionSet[T](user, r.json)
          Cache.set(url, sperms, cacheTime)
          sperms
        }
      }
    }
  }

  def setScope(user: T, id: String, data: Map[String,List[String]]): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
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
        Cache remove(userId)
        Cache.remove(enc(requestUrl, userId))
        r.status == OK
      }
    }
  }

  def removeGroup(groupId: String, userId: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
        .withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      checkError(response).right.map { r =>
        Cache remove(userId)
        Cache.remove(enc(requestUrl, userId))
        r.status == OK
      }
    }
  }
}