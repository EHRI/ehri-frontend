package backend.rest

import javax.inject.Inject

import acl.{ItemPermissionSet, GlobalPermissionSet}
import backend.rest.Constants._
import defines.{ContentTypes, EntityType}
import play.api.libs.json._
import utils._
import utils.caching.FutureCache

import scala.concurrent.{ExecutionContext, Future}
import play.api.mvc.Headers
import play.api.cache.CacheApi
import play.api.libs.ws.{StreamedResponse, WSClient, WSResponse}
import backend._
import scala.concurrent.Future.{successful => immediate}


case class RestApi @Inject ()(eventHandler: EventHandler, cache: CacheApi, config: play.api.Configuration, ws: WSClient) extends DataApi {
  override def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext) =
    RestApiHandle(eventHandler)(
      cache: CacheApi, config, apiUser, executionContext, ws)
}

case class RestApiHandle(eventHandler: EventHandler)(
  implicit val cache: CacheApi,
  val config: play.api.Configuration,
  val apiUser: ApiUser,
  val executionContext: ExecutionContext,
  val ws: WSClient
) extends DataApiHandle with RestService with RestContext  {

  override def withEventHandler(eventHandler: EventHandler) = this.copy(eventHandler = eventHandler)

  // Direct API query
  override def query(urlPart: String, headers: Headers = Headers(), params: Map[String,Seq[String]] = Map.empty): Future[WSResponse] =
    userCall(enc(baseUrl, urlPart) + (if(params.nonEmpty) "?" + joinQueryString(params) else ""))
      .withHeaders(headers.headers: _*).get()

  override def stream(urlPart: String, headers: Headers = Headers(), params: Map[String,Seq[String]] = Map.empty): Future[StreamedResponse] =
    userCall(enc(baseUrl, urlPart) + (if(params.nonEmpty) "?" + joinQueryString(params) else ""))
      .withHeaders(headers.headers: _*).withMethod("GET").stream()

  override def createNewUserProfile[T <: WithId: Readable](data: Map[String,String] = Map.empty, groups: Seq[String] = Seq.empty): Future[T] = {
    userCall(enc(baseUrl, "admin", "create-default-user-profile"))
      .withQueryString(groups.map(group => Constants.GROUP_PARAM -> group): _*)
      .post(Json.toJson(data)).map { response =>
      val item = checkErrorAndParse(response)(implicitly[Readable[T]].restReads)
      eventHandler.handleCreate(item.id)
      item
    }
  }

  override def get[MT](resource: Resource[MT], id: String): Future[MT] = {
    val url = canonicalUrl(id)(resource)
    cache.get[JsValue](url).map { json =>
      Future.successful(jsonReadToRestError(json, resource.restReads))
    }.getOrElse {
      userCall(url, resource.defaultParams).get().map { response =>
        val item = checkErrorAndParse(response, context = Some(url))(resource.restReads)
        cache.set(url, response.json, cacheTime)
        item
      }
    }
  }

  override def get[MT: Resource](id: String): Future[MT] = {
    get(Resource[MT], id)
  }

  override def create[MT <: WithId : Resource, T: Writable](item: T, accessors: Seq[String] = Nil,
                                                            params: Map[String,Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(unpack(params):_*)
      .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleCreate(created.id)
      created
    }
  }

  override def createInContext[MT: Resource, T: Writable, TT <: WithId : Readable](id: String, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[TT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType, id)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(unpack(params):_*)
      .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(Readable[TT].restReads)
      // also reindex parent since this will update child count caches
      eventHandler.handleUpdate(id)
      eventHandler.handleCreate(created.id)
      cache.remove(canonicalUrl(id))
      created
    }
  }

  override def update[MT: Resource, T: Writable](id: String, item: T, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
      .put(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      item
    }
  }

  override def patch[MT: Resource](id: String, data: JsObject, logMsg: Option[String] = None): Future[MT] = {
    val item = Json.obj(Entity.TYPE -> Resource[MT].entityType, Entity.DATA -> data)
    val url = enc(typeBaseUrl, Resource[MT].entityType, id)
    userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
      .put(item).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      item
    }
  }

  override def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit] = {
    userCall(enc(typeBaseUrl, Resource[MT].entityType, id)).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      cache.remove(canonicalUrl(id))
    }
  }

  override def list[MT: Resource](params: PageParams = PageParams.empty): Future[Page[MT]] =
    list(Resource[MT], params)

  override def list[MT](resource: Resource[MT], params: PageParams): Future[Page[MT]] = {
    val url = enc(typeBaseUrl, resource.entityType)
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(resource.restReads)
    }
  }

  override def children[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty): Future[Page[CMT]] = {
    val url: String = enc(typeBaseUrl, Resource[MT].entityType, id, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[CMT].restReads)
    }
  }

  override def count[MT: Resource](): Future[Long] =
    getTotal(enc(typeBaseUrl, Resource[MT].entityType))

  override def countChildren[MT: Resource](id: String): Future[Long] =
    getTotal(enc(typeBaseUrl, Resource[MT].entityType, id, "list"))

  private val genericItemUrl = enc(baseUrl, "entities")

  override def getAny[MT: Readable](id: String): Future[MT] = {
    val url: String = enc(genericItemUrl, id)
    BackendRequest(url).withHeaders(authHeaders.toSeq: _*).get().map { response =>
      checkErrorAndParse(response, context = Some(url))(implicitly[Readable[MT]].restReads)
    }
  }

  override def fetch[MT: Readable](ids: Seq[String] = Seq.empty, gids: Seq[Long] = Seq.empty): Future[Seq[MT]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty && gids.isEmpty) immediate(Seq.empty[MT]) else {
      val payload: JsArray = Json.toJson(ids).as[JsArray] ++ Json.toJson(gids).as[JsArray]
      userCall(enc(genericItemUrl)).post(payload).map { response =>
        checkErrorAndParse(response)(Reads.seq(implicitly[Readable[MT]].restReads))
      }
    }
  }

  override def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT] = {
    val url: String = enc(genericItemUrl, id, "access")
    userCall(url)
      .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
      .post("").map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      cache.remove(canonicalUrl(id))
      eventHandler.handleUpdate(id)
      r
    }
  }

  override def promote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).post("").map(itemResponse(id, _))

  override def removePromotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).delete().map(itemResponse(id, _))

  override def demote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "demote")).post("").map(itemResponse(id, _))

  override def removeDemotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "demote")).delete().map(itemResponse(id, _))

  override def links[A: Readable](id: String): Future[Page[A]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(genericItemUrl, id, "links")).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(Readable[A].restReads)
    }
  }

  override def annotations[A: Readable](id: String): Future[Page[A]] = {
    val url = enc(genericItemUrl, id, "annotations")
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def itemPermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]] =
    listWithUrl(enc(genericItemUrl, id, "permission-grants"), params)

  override def scopePermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]] =
    listWithUrl(enc(genericItemUrl, id, "scope-permission-grants"), params)

  override def history[A: Readable](id: String, params: RangeParams,
                                    filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(genericItemUrl, id, "events")
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def createDescription[MT: Resource, DT: Writable](id: String, item: DT, logMsg: Option[String] = None): Future[DT] = {
    val url: String = enc(genericItemUrl, id, "descriptions")
    userCall(url).withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[DT].restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(Writable[DT].restFormat)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      desc
    }
  }

  override def updateDescription[MT: Resource, DT: Writable](id: String, did: String, item: DT, logMsg: Option[String] = None): Future[DT] = {
    val url: String = enc(genericItemUrl, id, "descriptions", did)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
      .put(Json.toJson(item)(Writable[DT].restFormat)).map { response =>
      val desc: DT = checkErrorAndParse(response, context = Some(url))(Writable[DT].restFormat)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      desc
    }
  }

  override def deleteDescription[MT: Resource](id: String, did: String, logMsg: Option[String] = None): Future[Unit] = {
    userCall(enc(genericItemUrl, id, "descriptions", did))
      .withHeaders(msgHeader(logMsg): _*).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(did)
      cache.remove(canonicalUrl(id))
    }
  }

  override def createAccessPoint[MT: Resource, AP: Writable](id: String, did: String, item: AP, logMsg: Option[String] = None): Future[AP] = {
    val url: String = enc(genericItemUrl, id, "descriptions", did, "access-points")
    userCall(url)
      .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[AP].restFormat)).map { response =>
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      checkErrorAndParse(response, context = Some(url))(Writable[AP].restFormat)
    }
  }

  override def deleteAccessPoint[MT: Resource](id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit] = {
    val url = enc(genericItemUrl, id, "descriptions", did, "access-points", apid)
    userCall(url).withHeaders(msgHeader(logMsg): _*).delete().map { response =>
      checkError(response)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
    }
  }

  override def userActions[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.UserProfile, userId, "actions")
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def userEvents[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.UserProfile, userId, "events")
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }


  override def versions[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(genericItemUrl, id, "versions")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def createAnnotation[A <: WithId : Readable, AF: Writable](id: String, ann: AF, accessors: Seq[String] =
  Nil, subItem: Option[String] = None): Future[A] = {
    val url: String = enc(typeBaseUrl, EntityType.Annotation)
    userCall(url)
      .withQueryString(TARGET_PARAM -> id)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(subItem.toSeq.map(a => BODY_PARAM -> a): _*)
      .post(Json.toJson(ann)(Writable[AF].restFormat)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A].restReads)
      eventHandler.handleCreate(annotation.id)
      annotation
    }
  }

  @Deprecated
  override def createAnnotationForDependent[A <: WithId : Readable, AF: Writable](id: String, did: String, ann: AF, accessors: Seq[String] = Nil): Future[A] = {
    createAnnotation(id, ann, accessors, Some(did))
  }

  override def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, src: String, link: AF, accessPoint: Option[String] = None): Future[A] = {
    val url: String = enc(typeBaseUrl, EntityType.Link)
    userCall(url).withQueryString(TARGET_PARAM -> id, SOURCE_PARAM -> src)
      .withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(Writable[AF].restFormat)).map { response =>
      cache.remove(canonicalUrl[MT](id))
      val link: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A].restReads)
      eventHandler.handleCreate(link.id)
      link
    }
  }

  /**
   * Create multiple links. NB: This function is NOT transactional.
   */
  override def linkMultiple[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, srcToLinks: Seq[(String,AF,Option[String])]): Future[Seq[A]] = {
    val done: Future[Seq[A]] = Future.sequence {
      srcToLinks.map { case (other, ann, accessPoint) =>
        linkItems(id, other, ann, accessPoint)
      }
    }
    done.map { r =>
      cache.remove(canonicalUrl(id))
      r
    }
  }

  override def events[A: Readable](params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.SystemEvent)
    fetchRange(userCall(url, filters.toSeq), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(typeBaseUrl, EntityType.SystemEvent, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def addReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] =
    userCall(enc(typeBaseUrl,  EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).post("").map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def deleteReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) immediate(())
    else userCall(enc(typeBaseUrl,  EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map ( id => ID_PARAM -> id): _*).delete().map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def moveReferences[MT: Resource](fromVc: String, toVc: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) immediate(())
    else userCall(enc(typeBaseUrl,  EntityType.VirtualUnit, fromVc, "includes", toVc))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).post("").map { _ =>
      // Update both source and target sets in the index
      cache.remove(canonicalUrl(fromVc))
      cache.remove(canonicalUrl(toVc))
      eventHandler.handleUpdate(fromVc)
      eventHandler.handleUpdate(toVc)
    }

  private val permissionRequestUrl = enc(baseUrl, "permissions")

  override def permissionGrants[A: Readable](userId: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(permissionRequestUrl, userId, "permission-grants")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  override def globalPermissions(userId: String): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId)
    FutureCache.getOrElse[GlobalPermissionSet](url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data))
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def itemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "item", id)
    FutureCache.getOrElse[ItemPermissionSet](url, cacheTime) {
      userCall(url).get().map { response =>
        checkErrorAndParse(response, context = Some(url))(ItemPermissionSet.restReads(contentType))
      }
    }
  }

  override def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "item", id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data)).map { response =>
        checkErrorAndParse(response, context = Some(url))(ItemPermissionSet.restReads(contentType))
      }
    }
  }

  override def scopePermissions(userId: String, id: String): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "scope", id)
    FutureCache.getOrElse[GlobalPermissionSet](url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setScopePermissions(userId: String, id: String, data: Map[String,Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "scope", id)
    FutureCache.set(url, cacheTime) {
      userCall(url).post(Json.toJson(data))
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def addGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit] = {
    userCall(enc(typeBaseUrl, EntityType.Group, groupId, userId)).post(Map[String, Seq[String]]()).map { response =>
      checkError(response)
      cache.remove(canonicalUrl[UT](userId))
      cache.remove(canonicalUrl[GT](groupId))
      cache.remove(enc(permissionRequestUrl, userId))
    }
  }

  override def removeGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit] = {
    userCall(enc(typeBaseUrl, EntityType.Group, groupId, userId)).delete().map { response =>
      checkError(response)
      cache.remove(canonicalUrl[UT](userId))
      cache.remove(canonicalUrl[GT](groupId))
      cache.remove(enc(permissionRequestUrl, userId))
    }
  }

  private val userRequestUrl = enc(typeBaseUrl,  EntityType.UserProfile)

  private def followingUrl(userId: String) = enc(userRequestUrl, userId, "following")

  private def watchingUrl(userId: String) = enc(userRequestUrl, userId, "watching")

  private def blockedUrl(userId: String) = enc(userRequestUrl, userId, "blocked")

  private def isFollowingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-following", otherId)

  private def isWatchingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-watching", otherId)

  private def isBlockingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-blocking", otherId)

  override def follow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      checkError(r)
      cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      cache.remove(followingUrl(userId))
      cache.remove(canonicalUrl(userId))
    }
  }

  override def unfollow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      checkError(r)
      cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      cache.remove(followingUrl(userId))
      cache.remove(canonicalUrl(userId))
    }
  }

  override def isFollowing(userId: String, otherId: String): Future[Boolean] = {
    val url = isFollowingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def isFollower(userId: String, otherId: String): Future[Boolean] = {
    userCall(enc(userRequestUrl, userId, "is-follower", otherId)).get().map { r =>
      checkErrorAndParse[Boolean](r)
    }
  }

  override def followers[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]] = {
    val url: String = enc(userRequestUrl, userId, "followers")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[U].restReads)
    }
  }

  override def following[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]] = {
    val url: String = followingUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[U].restReads)
    }
  }

  override def watching[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "watching")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def watch(userId: String, otherId: String): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def unwatch(userId: String, otherId: String): Future[Unit] = {
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      cache.remove(watchingUrl(userId))
      checkError(r)
    }
  }

  override def isWatching(userId: String, otherId: String): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def blocked[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = blockedUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def block(userId: String, otherId: String): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).post("").map { r =>
      cache.set(isBlockingUrl(userId, otherId), true, cacheTime)
      cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def unblock(userId: String, otherId: String): Future[Unit] = {
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).delete().map { r =>
      cache.set(isBlockingUrl(userId, otherId), false, cacheTime)
      cache.remove(blockedUrl(userId))
      checkError(r)
    }
  }

  override def isBlocking(userId: String, otherId: String): Future[Boolean] = {
    val url = isBlockingUrl(userId, otherId)
    FutureCache.getOrElse[Boolean](url) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def userAnnotations[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "annotations")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def userLinks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "links")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  override def userBookmarks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "virtual-units")
    userCall(url).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A].restReads)
    }
  }

  // Helpers

  private def getTotal(url: String): Future[Long] =
    userCall(url).withMethod("HEAD").execute().map { response =>
      parsePagination(response, context = Some(url)).map { case (_, _, total) =>
        total.toLong
      }.getOrElse(-1L)
    }

  private def itemResponse[MT: Resource](id: String, response: WSResponse): MT = {
    val item: MT = checkErrorAndParse(response)(Resource[MT].restReads)
    cache.remove(canonicalUrl(id))
    eventHandler.handleUpdate(id)
    item
  }

  private def listWithUrl[A: Readable](url: String, params: PageParams): Future[Page[A]] = {
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }
}

object RestApi {
  def withNoopHandler(cache: CacheApi, config: play.api.Configuration, ws: WSClient): DataApi =
    new RestApi(new EventHandler {
      def handleCreate(id: String) = ()
      def handleUpdate(id: String) = ()
      def handleDelete(id: String) = ()
    }, cache: CacheApi, config, ws)
}
