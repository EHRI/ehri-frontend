package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import acl._
import models.base.Accessor
import defines._
import models.PermissionGrant
import play.api.libs.json.Json
import play.api.Play.current
import play.api.cache.Cache
import utils.{Page, FutureCache, PageParams}
import backend.{Permissions, EventHandler, ApiUser}


trait RestPermissions extends Permissions with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  implicit val permissionGrantMetaReads = PermissionGrant.Converter.restReads
  implicit val pageReads = Page.pageReads(permissionGrantMetaReads)

  private def baseUrl = s"http://$host:$port/$mount"
  private def requestUrl = s"$baseUrl/permission"

  def listPermissionGrants[T <: Accessor](user: T, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "list", user.id), params)

  def listItemPermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "listForItem", id), params)

  def listScopePermissionGrants(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[PermissionGrant]] =
    listWithUrl(enc(requestUrl, "listForScope", id), params)

  private def listWithUrl(url: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[PermissionGrant]] = {
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage[PermissionGrant](response)
    }
  }

  def getGlobalPermissions[T <: Accessor](user: T)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    FutureCache.getOrElse[GlobalPermissionSet[T]](url, cacheTime) {
      userCall(url).get().map { response =>
        GlobalPermissionSet[T](user, checkError(response).json)
      }
    }
  }

  def setGlobalPermissions[T <: Accessor](user: T, data: Map[String, List[String]])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data)).map { response =>
        GlobalPermissionSet[T](user, checkError(response).json)
      }
    }
  }

  def getItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    FutureCache.getOrElse[ItemPermissionSet[T]](url, cacheTime) {
      userCall(url).get().map { response =>
        ItemPermissionSet[T](user, contentType, checkError(response).json)
      }
    }
  }

  def setItemPermissions[T <: Accessor](user: T, contentType: ContentTypes.Value, id: String, data: List[String])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[ItemPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data)).map { response =>
        ItemPermissionSet[T](user, contentType, checkError(response).json)
      }
    }
  }

  def getScopePermissions[T <: Accessor](user: T, id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    FutureCache.getOrElse[GlobalPermissionSet[T]](url, cacheTime) {
      userCall(url).get().map { response =>
        GlobalPermissionSet[T](user, checkError(response).json)
      }
    }
  }

  def setScopePermissions[T <: Accessor](user: T, id: String, data: Map[String,List[String]])(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[GlobalPermissionSet[T]] = {
    val url = enc(requestUrl, user.id, "scope", id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data)).map { response =>
        GlobalPermissionSet[T](user, checkError(response).json)
      }
    }
  }

  def addGroup(groupId: String, userId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).post(Map[String, List[String]]()).map { response =>
      checkError(response)
      Cache.remove(userId)
      Cache.remove(groupId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }

  def removeGroup(groupId: String, userId: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).delete().map { response =>
      checkError(response)
      Cache.remove(userId)
      Cache.remove(groupId)
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }
}


case class PermissionDAO(eventHandler: EventHandler) extends RestPermissions
