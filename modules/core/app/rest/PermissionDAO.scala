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

  def list[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "page", user.id), params)

  def listForItem(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "pageForItem", id), params)

  def listForScope(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "pageForScope", id), params)

  private def listWithUrl(url: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] = {
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[PermissionGrant]](response)
    }
  }

  def get[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        val gperms = GlobalPermissionSet[T](user, checkError(response).json)
        Cache.set(url, gperms, cacheTime)
        gperms
      }
    }
  }

  def set[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      val gperms = GlobalPermissionSet[T](user, checkError(response).json)
      Cache.set(url, gperms, cacheTime)
      gperms
    }
  }

  def getItem[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    val cached = Cache.getAs[ItemPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      Logger.logger.debug("Fetch item perms: {}", url)
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        val iperms = ItemPermissionSet[T](user, contentType, checkError(response).json)
        Cache.set(url, iperms, cacheTime)
        iperms
      }
    }
  }

  def setItem[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    WS.url(url)
        .withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      val iperms = ItemPermissionSet[T](user, contentType, checkError(response).json)
      Cache.set(url, iperms, cacheTime)
      iperms
    }
  }

  def getScope[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      Logger.logger.debug("Fetch scoped perms: {}", url)
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        val sperms = GlobalPermissionSet[T](user, checkError(response).json)
        Cache.set(url, sperms, cacheTime)
        sperms
      }
    }
  }

  def setScope[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).post(Json.toJson(data)).map { response =>
      val sperms = GlobalPermissionSet[T](user, checkError(response).json)
      Cache.set(url, sperms, cacheTime)
      sperms
    }
  }

  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
        .withHeaders(authHeaders.toSeq: _*).post(Map[String, List[String]]()).map { response =>
      checkError(response)
      Cache remove(userId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }

  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    WS.url(enc(baseUrl, EntityType.Group, groupId, userId))
        .withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      checkError(response)
      Cache remove(userId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }
}
