package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import acl._
import models.base.Accessor
import defines._
import models.PermissionGrant
import play.api.libs.json.Json
import play.api.Play.current
import play.api.cache.Cache
import play.api.Logger
import utils.PageParams


case class PermissionDAO(eventHandler: RestEventHandler) extends RestDAO {

  import Constants._
  import play.api.http.Status._

  implicit val permissionGrantMetaReads = PermissionGrant.Converter.restReads
  implicit val pageReads = Page.pageReads(permissionGrantMetaReads)

  def baseUrl = "http://%s:%d/%s".format(host, port, mount)
  def requestUrl = "%s/permission".format(baseUrl)

  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "page", user.id), params)

  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "pageForItem", id), params)

  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "pageForScope", id), params)

  private def listWithUrl(url: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[PermissionGrant]] = {
    userCall(url).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[Page[PermissionGrant]](response)
    }
  }

  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      userCall(url).get.map { response =>
        val gperms = GlobalPermissionSet[T](user, checkError(response).json)
        Cache.set(url, gperms, cacheTime)
        gperms
      }
    }
  }

  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    userCall(url).post(Json.toJson(data)).map { response =>
      val gperms = GlobalPermissionSet[T](user, checkError(response).json)
      Cache.set(url, gperms, cacheTime)
      gperms
    }
  }

  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    val cached = Cache.getAs[ItemPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      userCall(url).get.map { response =>
        val iperms = ItemPermissionSet[T](user, contentType, checkError(response).json)
        Cache.set(url, iperms, cacheTime)
        iperms
      }
    }
  }

  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    userCall(url).post(Json.toJson(data)).map { response =>
      val iperms = ItemPermissionSet[T](user, contentType, checkError(response).json)
      Cache.set(url, iperms, cacheTime)
      iperms
    }
  }

  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    var cached = Cache.getAs[GlobalPermissionSet[T]](url)
    if (cached.isDefined) Future.successful(cached.get)
    else {
      userCall(url).get.map { response =>
        val sperms = GlobalPermissionSet[T](user, checkError(response).json)
        Cache.set(url, sperms, cacheTime)
        sperms
      }
    }
  }

  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    userCall(url).post(Json.toJson(data)).map { response =>
      val sperms = GlobalPermissionSet[T](user, checkError(response).json)
      Cache.set(url, sperms, cacheTime)
      sperms
    }
  }

  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).post(Map[String, List[String]]()).map { response =>
      checkError(response)
      Cache remove(userId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }

  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).delete.map { response =>
      checkError(response)
      Cache remove(userId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }
}
