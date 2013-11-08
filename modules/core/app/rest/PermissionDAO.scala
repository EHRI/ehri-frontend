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
import utils.PageParams


case class PermissionDAO() extends RestDAO {

  import Constants._
  import play.api.http.Status._

  implicit val permissionGrantMetaReads = PermissionGrant.Converter.restReads
  implicit val pageReads = Page.pageReads(permissionGrantMetaReads)

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "page", user.id), params)

  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForItem", id), params)

  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]] =
    listWithUrl(enc(requestUrl, "pageForScope", id), params)

  private def listWithUrl(url: String, params: PageParams)(implicit apiUser: ApiUser): Future[Either[RestError, Page[PermissionGrant]]] = {
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[PermissionGrant]](response)
    }
  }

  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]] = {
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

  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]] = {
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

  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]] = {
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

  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[Either[RestError, ItemPermissionSet[T]]] = {
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

  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]] = {
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

  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[Either[RestError, GlobalPermissionSet[T]]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      checkError(response).right.map { r =>
        val sperms = GlobalPermissionSet[T](user, r.json)
        Cache.set(url, sperms, cacheTime)
        sperms
      }
    }
  }

  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
        .withHeaders(authHeaders.toSeq: _*).post(Map[String, List[String]]()).map { response =>
      checkError(response).right.map { r =>
        Cache remove(userId)
        Cache.remove(enc(requestUrl, userId))
        r.status == OK
      }
    }
  }

  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]] = {
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