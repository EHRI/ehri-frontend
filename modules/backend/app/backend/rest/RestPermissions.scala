package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import acl._
import defines._
import play.api.libs.json.Json
import play.api.cache.Cache
import utils.{Page, PageParams}
import backend._
import caching.FutureCache


trait RestPermissions extends Permissions with RestDAO {

  val eventHandler: EventHandler
  implicit def apiUser: ApiUser
  implicit def executionContext: ExecutionContext

  import Constants._

  private def requestUrl = s"$baseUrl/permission"

  def listPermissionGrants[A](userId: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] =
    listWithUrl(enc(requestUrl, "list", userId), params)

  def listItemPermissionGrants[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] =
    listWithUrl(enc(requestUrl, "listForItem", id), params)

  def listScopePermissionGrants[A](id: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] =
    listWithUrl(enc(requestUrl, "listForScope", id), params)

  private def listWithUrl[A](url: String, params: PageParams)(implicit rd: BackendReadable[A]): Future[Page[A]] = {
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  override def getGlobalPermissions(userId: String): Future[GlobalPermissionSet] = {
    val url = enc(requestUrl, userId)
    FutureCache.getOrElse[GlobalPermissionSet](url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(requestUrl, userId)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data))
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def getItemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet] = {
    val url = enc(requestUrl, userId, id)
    FutureCache.getOrElse[ItemPermissionSet](url, cacheTime) {
      userCall(url).get().map { response =>
        checkErrorAndParse(response, context = Some(url))(ItemPermissionSet.restReads(contentType))
      }
    }
  }

  override def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet] = {
    val url = enc(requestUrl, userId, id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data)).map { response =>
        checkErrorAndParse(response, context = Some(url))(ItemPermissionSet.restReads(contentType))
      }
    }
  }

  override def getScopePermissions(userId: String, id: String): Future[GlobalPermissionSet] = {
    val url = enc(requestUrl, userId, "scope", id)
    FutureCache.getOrElse[GlobalPermissionSet](url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setScopePermissions(userId: String, id: String, data: Map[String,Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(requestUrl, userId, "scope", id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data))
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def addGroup[GT,UT](groupId: String, userId: String)(implicit gr: BackendResource[GT], ur: BackendResource[UT]): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).post(Map[String, Seq[String]]()).map { response =>
      checkError(response)
      Cache.remove(canonicalUrl[UT](userId))
      Cache.remove(canonicalUrl[GT](groupId))
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }

  override def removeGroup[GT,UT](groupId: String, userId: String)(implicit gr: BackendResource[GT], ur: BackendResource[UT]): Future[Boolean] = {
    userCall(enc(baseUrl, EntityType.Group, groupId, userId)).delete().map { response =>
      checkError(response)
      Cache.remove(canonicalUrl[UT](userId))
      Cache.remove(canonicalUrl[GT](groupId))
      Cache.remove(enc(requestUrl, userId))
      true
    }
  }
}
