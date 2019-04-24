package services.data

import acl.{GlobalPermissionSet, ItemPermissionSet}
import akka.stream.scaladsl.{JsonFraming, Source}
import akka.util.ByteString
import defines.{ContentTypes, EntityType}
import javax.inject.Inject
import play.api.cache.SyncCacheApi
import play.api.http.{ContentTypeOf, HeaderNames, HttpVerbs}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Headers
import services._
import services.data.Constants._
import utils._
import utils.caching.FutureCache

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class DataApiService @Inject()(eventHandler: EventHandler, cache: SyncCacheApi, config: play.api.Configuration, ws: WSClient) extends DataApi {
  override def withContext(apiUser: ApiUser)(implicit executionContext: ExecutionContext): DataApiServiceHandle =
    DataApiServiceHandle(eventHandler)(
      cache: SyncCacheApi, config, apiUser, executionContext, ws)
}

case class DataApiServiceHandle(eventHandler: EventHandler)(
  implicit val cache: SyncCacheApi,
  val config: play.api.Configuration,
  val apiUser: ApiUser,
  val executionContext: ExecutionContext,
  val ws: WSClient
) extends DataApiHandle with RestService with DataApiContext {

  private val cacheTime: Duration = config.get[Duration]("ehri.backend.cacheExpiration")

  override def withEventHandler(eventHandler: EventHandler): DataApiServiceHandle = this.copy(eventHandler = eventHandler)

  override def status(): Future[String] = {
    // Using WS directly here to avoid caching and logging
    ws.url(s"$baseUrl/classes/${EntityType.Group}/admin")
      .withHttpHeaders(HeaderNames.ACCEPT -> play.api.http.ContentTypes.JSON).get().map { r =>
      r.json.validate[JsObject].fold(err => throw BadJson(err), _ => "ok")
    } recover {
      case err => throw ServiceOffline(err.getMessage, err)
    }
  }

  // Direct API query
  override def query(urlPart: String, headers: Headers = Headers(), params: Map[String, Seq[String]] = Map.empty): Future[WSResponse] =
    userCall(enc(baseUrl, urlPart) + (if (params.nonEmpty) "?" + utils.http.joinQueryString(params) else ""))
      .withHeaders(headers.headers: _*).get()

  override def stream(urlPart: String, headers: Headers = Headers(), params: Map[String,Seq[String]] = Map.empty): Future[WSResponse] =
    userCall(enc(baseUrl, urlPart) + (if(params.nonEmpty) "?" + utils.http.joinQueryString(params) else ""))
      .withHeaders(headers.headers: _*).withMethod("GET").stream()

  override def createNewUserProfile[T <: WithId : Readable](data: Map[String, String] = Map.empty, groups: Seq[String] = Seq.empty): Future[T] = {
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
    params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType)
    userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(unpack(params): _*)
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
      .withQueryString(unpack(params): _*)
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

  override def update[MT: Resource, T: Writable](id: String, item: T, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
      .withQueryString(unpack(params): _*)
      .put(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleUpdate(id)
      cache.remove(canonicalUrl(id))
      item
    }
  }

  override def patch[MT: Resource](id: String, data: JsObject, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val item = Json.obj("type" -> Resource[MT].entityType, "data" -> data)
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

  override def parent[MT: Resource, PMT: Resource](id: String, parentIds: Seq[String]): Future[MT] = {
    val url = enc(typeBaseUrl, Resource[MT].entityType, id, "parent")
    userCall(url).withQueryString(parentIds.map(n => ID_PARAM -> n): _*).post().map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      parentIds.foreach(id => cache.remove(canonicalUrl[PMT](id)(Resource[PMT])))
      cache.remove(canonicalUrl[MT](id))
      eventHandler.handleUpdate(parentIds :+ id: _*)
      r
    }
  }

  override def list[MT: Resource](params: PageParams = PageParams.empty): Future[Page[MT]] =
    list(Resource[MT], params)

  override def list[MT](resource: Resource[MT], params: PageParams): Future[Page[MT]] =
    listWithUrl[MT](enc(typeBaseUrl, resource.entityType), params)(resource)

  override def stream[MT: Resource](): Source[MT, _] =
    streamWithUrl[MT](enc(typeBaseUrl, Resource[MT].entityType))

  override def children[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty, all: Boolean = false): Future[Page[CMT]] =
    listWithUrl[CMT](enc(typeBaseUrl, Resource[MT].entityType, id, "list"), params, Seq("all" -> all.toString))

  override def streamChildren[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty.withoutLimit, all: Boolean = false): Source[CMT, _] =
    streamWithUrl[CMT](enc(typeBaseUrl, Resource[MT].entityType, id, "list"), Seq("all" -> all.toString))

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

  override def fetch[MT: Readable](ids: Seq[String] = Seq.empty, gids: Seq[Long] = Seq.empty): Future[Seq[Option[MT]]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty && gids.isEmpty) immediate(Seq.empty[Option[MT]]) else {
      val payload: JsArray = Json.toJson(ids).as[JsArray] ++ Json.toJson(gids).as[JsArray]
      userCall(enc(genericItemUrl)).post(payload).map { response =>
        checkErrorAndParse(response)(Reads.seq(
          Reads.optionWithNull(implicitly[Readable[MT]].restReads)))
      }
    }
  }

  override def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT] = {
    val url: String = enc(genericItemUrl, id, "access")
    userCall(url)
      .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
      .post().map { response =>
      val r = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      cache.remove(canonicalUrl(id))
      eventHandler.handleUpdate(id)
      r
    }
  }

  override def promote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).post().map(itemResponse(id, _))

  override def removePromotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).delete().map(itemResponse(id, _))

  override def demote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "demote")).post().map(itemResponse(id, _))

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
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A].restReads))
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
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A].restReads))
  }

  override def userEvents[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.UserProfile, userId, "events")
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A].restReads))
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

  override def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, to: String, link: AF, accessPoint: Option[String] = None, directional: Boolean = false): Future[A] = {
    val url: String = enc(typeBaseUrl, EntityType.Link)
    userCall(url)
      .withQueryString(
        SOURCE_PARAM -> id,
        TARGET_PARAM -> to,
        "directional" -> directional.toString
      )
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
  override def linkMultiple[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, srcToLinks: Seq[(String, AF, Option[String])]): Future[Seq[A]] = {
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
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A].restReads))
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
    userCall(enc(typeBaseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).post().map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def deleteReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) immediate(())
    else userCall(enc(typeBaseUrl, EntityType.VirtualUnit, vcId, "includes"))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).delete().map { _ =>
      eventHandler.handleUpdate(vcId)
      cache.remove(canonicalUrl(vcId))
    }

  override def moveReferences[MT: Resource](fromVc: String, toVc: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) immediate(())
    else userCall(enc(typeBaseUrl, EntityType.VirtualUnit, fromVc, "includes", toVc))
      .withQueryString(ids.map(id => ID_PARAM -> id): _*).post().map { _ =>
      // Update both source and target sets in the index
      cache.remove(canonicalUrl(fromVc))
      cache.remove(canonicalUrl(toVc))
      eventHandler.handleUpdate(toVc, fromVc)
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

  override def setScopePermissions(userId: String, id: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet] = {
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

  private val userRequestUrl = enc(typeBaseUrl, EntityType.UserProfile)

  private def followingUrl(userId: String) = enc(userRequestUrl, userId, "following")

  private def watchingUrl(userId: String) = enc(userRequestUrl, userId, "watching")

  private def blockedUrl(userId: String) = enc(userRequestUrl, userId, "blocked")

  private def isFollowingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-following", otherId)

  private def isWatchingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-watching", otherId)

  private def isBlockingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-blocking", otherId)

  override def follow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).post().map { r =>
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
    userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).post().map { r =>
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
    userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).post().map { r =>
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

  private implicit val seqTuple2Format: Format[Seq[(String, String)]] = Format(
    Reads.seq[List[String]].map(_.collect { case a :: b :: _ => a -> b }),
    Writes { s => Json.toJson(s.map(t => Seq(t._1, t._2))) }
  )

  override def rename(mapping: Seq[(String, String)]): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "rename")
    userCall(url)
      .withQueryString("commit" -> true.toString)
      .post(Json.toJson(mapping))
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def reparent(mapping: Seq[(String, String)], commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "reparent")
    userCall(url).withQueryString("commit" -> commit.toString)
      .post(Json.toJson(mapping))
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def regenerateIdsForType(ct: ContentTypes.Value, tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "regenerate-ids-for-type", ct)
    userCall(url).withQueryString("tolerant" -> tolerant.toString, "commit" -> commit.toString)
      .withTimeout(20.minutes).post()
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def regenerateIdsForScope(scope: String, tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "regenerate-ids-for-scope", scope)
    userCall(url).withQueryString("tolerant" -> tolerant.toString, "commit" -> commit.toString)
      .withTimeout(20.minutes).post()
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def regenerateIds(ids: Seq[String], tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "regenerate-ids")
    userCall(url)
      .withQueryString("tolerant" -> tolerant.toString, "commit" -> commit.toString)
      .post(Json.toJson(ids.map(id => Seq(id))))
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  private implicit val seqTuple3Format: Format[Seq[(String, String, String)]] = Format(
    Reads.seq[List[String]].map(_.collect { case a :: b :: c :: _ => (a, b, c) }),
    Writes { s => Json.toJson(s.map(t => Seq(t._1, t._2, t._3))) }
  )

  override def findReplace(ct: ContentTypes.Value, et: EntityType.Value, property: String, from: String, to: String, commit: Boolean, logMsg: Option[String]): Future[Seq[(String, String, String)]] = {
    val url = enc(baseUrl, "tools", "find-replace")
    userCall(url)
      .withHeaders(msgHeader(logMsg): _*)
      .withQueryString("type" -> ct.toString, "subtype" -> et.toString,
        "property" -> property, "commit" -> commit.toString)
      .post(Map("from" -> Seq(from), "to" -> Seq(to)))
      .map(r => checkErrorAndParse[Seq[(String, String, String)]](r, Some(url)))
  }

  override def batchUpdate(data: Source[JsValue, _], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean): Future[BatchResult] = {
    val url = enc(baseUrl, "batch", "update")
    val src: Source[ByteString, _] = data
      .map(js => ByteString.fromArray(Json.toBytes(js)))
      .intersperse(ByteString('['), ByteString(','), ByteString(']'))
    implicit val ct: ContentTypeOf[Source[ByteString, _]] = ContentTypeOf(
      implicitly[ContentTypeOf[JsValue]].mimeType)
    userCall(url).withQueryString(
      "version" -> version.toString,
      "commit" -> commit.toString,
      "log" -> logMsg)
      .withQueryString(scope.toSeq.map(s => "scope" -> s): _*)
      .withBody(src)
      .withMethod(HttpVerbs.PUT)
      .execute()
      .map(r => checkErrorAndParse[BatchResult](r, Some(url)))
  }

  override def batchUpdate[T: Writable](data: Seq[T], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean): Future[BatchResult] = {
    implicit val writes: Writes[T] = Writable[T].restFormat
    val url = enc(baseUrl, "batch", "update")
    userCall(url).withQueryString(
      "version" -> version.toString,
      "commit" -> commit.toString,
      "log" -> logMsg)
      .withQueryString(scope.toSeq.map(s => "scope" -> s): _*)
      .put(Json.toJson(data))
      .map(r => checkErrorAndParse[BatchResult](r, Some(url)))
  }

  override def batchDelete(ids: Seq[String], scope: Option[String], logMsg: String, version: Boolean, commit: Boolean = false): Future[Int] = {
    val url = enc(baseUrl, "batch", "delete")
    userCall(url).withQueryString(
        "version" -> version.toString,
        "commit" -> commit.toString,
        "log" -> logMsg)
      .withQueryString(scope.toSeq.map(s => "scope" -> s): _*)
      .post(Json.toJson(ids.map(id => Seq(id))))
      .map(r => checkErrorAndParse[Int](r, Some(url)))
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

  private def listWithUrl[A: Readable](url: String, params: PageParams, extra: Seq[(String,String)] = Seq.empty): Future[Page[A]] = {
    userCall(url).withQueryString(params.queryParams ++ extra: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A].restReads)
    }
  }

  private def streamWithUrl[A: Readable](url: String, extra: Seq[(String, String)] = Seq.empty): Source[A, _] = {
    val reader = implicitly[Readable[A]]
    Source.fromFuture(userCall(url)
      .withQueryString(PageParams.empty.withoutLimit.queryParams ++ extra: _*)
      .stream().map (_.bodyAsSource
      .via(JsonFraming.objectScanner(Integer.MAX_VALUE))
      .map { bytes => Json.parse(bytes.utf8String).validate[A](reader.restReads) }
      .collect { case JsSuccess(item, _) => item}
    )).flatMapConcat(identity)
  }
}

object DataApiService {
  def withNoopHandler(cache: SyncCacheApi, config: play.api.Configuration, ws: WSClient): DataApi =
    new DataApiService(new EventHandler {
      def handleCreate(ids: String*): Unit = ()

      def handleUpdate(ids: String*): Unit = ()

      def handleDelete(ids: String*): Unit = ()
    }, cache: SyncCacheApi, config, ws)
}
