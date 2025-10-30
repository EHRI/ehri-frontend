package services.data

import org.apache.pekko.Done
import org.apache.pekko.stream.scaladsl.{JsonFraming, Source}
import org.apache.pekko.util.ByteString
import models.{ContentTypes, EntityType, GlobalPermissionSet, ItemPermissionSet, Readable, Resource, WithId, Writable}
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.http.{ContentTypeOf, HeaderNames, HttpVerbs, Status}
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.mvc.Headers
import services._
import services.data.Constants._
import services.data.WsDataService.parentIds
import utils._

import javax.inject.Inject
import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


case class WsDataServiceBuilder @Inject()(eventHandler: EventHandler, cache: AsyncCacheApi, config: Configuration, ws: WSClient) extends DataServiceBuilder {
  override def withContext(apiUser: DataUser)(implicit ec: ExecutionContext): WsDataService =
    WsDataService(eventHandler, config, cache, ws)(apiUser, ec)
}

object WsDataService {
  /*
   * Given an item ID, use the `sep` delimiter to infer its parent item IDs.
   */
  def parentIds(id: String, sep: String = "-"): Seq[String] = {
    id.split(sep).dropRight(1).foldLeft(Seq.empty[String]) { case (acc, part) =>
      acc.lastOption.map(last => acc :+ s"$last-$part").getOrElse(Seq(part))
    }
  }
}

case class WsDataService(eventHandler: EventHandler, config: Configuration, cache: AsyncCacheApi, ws: WSClient)(
  implicit apiUser: DataUser, ec: ExecutionContext
) extends DataService with WebServiceHelpers {

  private val cacheTime: Duration = config.get[Duration]("ehri.backend.cacheExpiration")

  override def withEventHandler(eventHandler: EventHandler): WsDataService = this.copy(eventHandler = eventHandler)

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

  override def stream(urlPart: String, headers: Headers = Headers(), params: Map[String,Seq[String]] = Map.empty, timeout: Option[Duration] = None): Future[WSResponse] = {
    val request = userCall(enc(baseUrl, urlPart) + (if (params.nonEmpty) "?" + utils.http.joinQueryString(params) else ""))
    val timeoutRequest = timeout.fold(request)(t => request.withTimeout(t))
    timeoutRequest.withHeaders(headers.headers: _*).withMethod(HttpVerbs.GET).stream()
  }

  override def createNewUserProfile[T <: WithId : Readable](data: Map[String, String] = Map.empty, groups: Seq[String] = Seq.empty): Future[T] = {
    userCall(enc(baseUrl, "admin", "create-default-user-profile"))
      .withQueryString(groups.map(group => Constants.GROUP_PARAM -> group): _*)
      .post(Json.toJson(data)).map { response =>
      val item = checkErrorAndParse(response)(implicitly[Readable[T]]._reads)
      eventHandler.handleCreate(item.idAndType)
      item
    }
  }

  override def get[MT](resource: Resource[MT], id: String): Future[MT] = {
    val url = canonicalUrl(id)(resource)
    // NB: Caching this is not straightforward since the types are generic and
    // we don't have the runtime type (or a `scala.reflect.ClassTag`) available.
    // As a result we cache the JSON response from the backend rather than the
    // item itself directly.
    // This requires doing a HEAD request to check the item exists and has the
    // right resource type on the backend to avoid potentially fetching something
    // from the cache as the wrong resource type. An alternate solution would be
    // to use the resource type as part of the cache key, but that would prevent
    // invalidating items without knowing their type first.
    userCall(url).head().flatMap { check =>
      if (check.status == Status.OK) {
        // Fetch the JSON object from the cache
        val jsonF = cache.getOrElseUpdate(itemCacheKey(id), cacheTime) {
          // Or else fetch it from the backend...
          userCall(url, resource.defaultParams).get().map(_.json)
        }
        // Deserialize it now...
        jsonF.map(json => jsonReadToRestError(json, resource._reads, context = Some(url)))
      } else {
        // If we fail to get an OK response run the request as normal
        // to get the correct error response from the server
        userCall(url, resource.defaultParams).get().map { r =>
          checkErrorAndParse(r)(resource._reads)
        }
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
      .post(Json.toJson(item)(Writable[T]._format)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(Resource[MT]._reads)
      eventHandler.handleCreate(created.idAndType)
      created
    }
  }

  override def createInContext[MT: Resource, T: Writable, TT <: WithId : Readable](id: String, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[TT] = {
    val entityType = Resource[MT].entityType
    val url = enc(typeBaseUrl, entityType, id)
    val callF = userCall(url)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(unpack(params): _*)
      .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[T]._format))
    for {
      response <- callF
      created = checkErrorAndParse(response, context = Some(url))(Readable[TT]._reads)
      // also reindex parent since this will update child count caches
      _ = eventHandler.handleUpdate(entityType -> id)
      _ = eventHandler.handleCreate(created.idAndType)
      _ <- invalidate(id)
    } yield created
  }

  override def update[MT: Resource, T: Writable](id: String, item: T, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val entityType = Resource[MT].entityType
    val url = enc(typeBaseUrl, entityType, id)
    val callF = userCall(url).withHeaders(msgHeader(logMsg): _*)
      .withQueryString(unpack(params): _*)
      .put(Json.toJson(item)(Writable[T]._format))
    for {
      response <- callF
      item = checkErrorAndParse(response, context = Some(url))(Resource[MT]._reads)
      _ = eventHandler.handleUpdate(entityType -> id)
      _ <- invalidate(id)
    } yield item
  }

  override def patch[MT: Resource](id: String, data: JsObject, params: Map[String, Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val entityType = Resource[MT].entityType
    val item = Json.obj("type" -> entityType, "data" -> data)
    val url = enc(typeBaseUrl, entityType, id)
    val callF = userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
      .put(item)
    for {
      response <- callF
      item = checkErrorAndParse(response, context = Some(url))(Resource[MT]._reads)
      _ = eventHandler.handleUpdate(entityType -> id)
      _ <- invalidate(id)
    } yield item
  }

  override def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit] = {
    val entityType = Resource[MT].entityType
    val callF = userCall(enc(typeBaseUrl, entityType, id)).delete()
    val parents = parentIds(id)
    for {
      response <- callF
      _ = checkError(response)
      // FIXME: we should update the holder here, but we can't because
      // the entity type is not available through the generic system.
      _ = eventHandler.handleDelete(entityType -> id)
      _ <- invalidate(parents)
    } yield ()
  }

  override def deleteChildren[MT: Resource, CMT: Resource](id: String, all: Boolean = false, logMsg: Option[String] = None): Future[Seq[String]] = {
    val entityType = Resource[MT].entityType
    val childEntityType = Resource[CMT].entityType
    val callF = userCall(enc(typeBaseUrl, entityType, id, "list"))
      .withQueryString("all" -> all.toString)
      .withTimeout(config.get[Duration]("ehri.admin.bulkOperations.timeout"))
      .delete()
    for {
      response <- callF
      deletedIds = checkErrorAndParse[Seq[List[String]]](response).collect { case id :: _ => id}
      _ = eventHandler.handleDelete(deletedIds.map(childEntityType -> _): _*)
      _ = eventHandler.handleUpdate(entityType -> id)
      _ <- invalidate(parentIds(id) ++ deletedIds)
    } yield deletedIds
  }

  override def rename[MT: Resource](id: String, local: String, logMsg: Option[String], check: Boolean = false): Future[Seq[(String, String)]] = {
    val entityType = Resource[MT].entityType
    val callF = userCall(enc(typeBaseUrl, entityType, id, "rename"))
      .withQueryString("check" -> check.toString)
      .post(local)
    for {
      response <- callF
      mappings = checkErrorAndParse[Seq[(String,String)]](response)
      // It's possible the ID won't have changed but the local identifier has (for ex:
      // if they're out-of-sync to start with.) In this case we need to invalidate the
      // existing ID anyway.
      _ = if (!mappings.toMap.contains(id)) eventHandler.handleUpdate(entityType -> id)
      _ <- if (mappings.toMap.contains(id)) immediate(Done) else cache.remove(id)
      _ = eventHandler.handleDelete(mappings.map(oldToNew => entityType -> oldToNew._1): _*)
      _ = eventHandler.handleUpdate(mappings.map(oldToNew => entityType -> oldToNew._2): _*)
      _ <- invalidate(mappings.map(_._1))
    } yield mappings
  }

  override def parent[MT: Resource, PMT: Resource](id: String, parentIds: Seq[String]): Future[MT] = {
    val parentEntityType = Resource[PMT].entityType
    val entityType = Resource[MT].entityType
    val url = enc(typeBaseUrl, entityType, id, "parent")
    val callF = userCall(url).withQueryString(parentIds.map(n => ID_PARAM -> n): _*).post()
    for {
      response <- callF
      item = checkErrorAndParse(response, context = Some(url))(Resource[MT]._reads)
      _ <- invalidate(parentIds)
      _ <- invalidate(id)
      _ = eventHandler.handleUpdate(parentIds.map(id => parentEntityType -> id): _*)
      _ = eventHandler.handleUpdate(entityType -> id)
    } yield item
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
      checkErrorAndParse(response, context = Some(url))(implicitly[Readable[MT]]._reads)
    }
  }

  override def fetch[MT: Readable](ids: Seq[String] = Seq.empty, gids: Seq[Long] = Seq.empty): Future[Seq[Option[MT]]] = {
    // NB: Using POST here because the list of IDs can
    // potentially overflow the GET param length...
    if (ids.isEmpty && gids.isEmpty) immediate(Seq.empty[Option[MT]]) else {
      val payload: JsArray = Json.toJson(ids).as[JsArray] ++ Json.toJson(gids).as[JsArray]
      userCall(enc(genericItemUrl)).post(payload).map { response =>
        checkErrorAndParse(response)(Reads.seq(
          Reads.optionWithNull(implicitly[Readable[MT]]._reads)))
      }
    }
  }

  override def setVisibility[MT: Resource](id: String, data: Seq[String]): Future[MT] = {
    val entityType = Resource[MT].entityType
    val url: String = enc(genericItemUrl, id, "access")
    val callF = userCall(url)
      .withQueryString(data.map(a => ACCESSOR_PARAM -> a): _*)
      .post()
    for {
      response <- callF
      item = checkErrorAndParse(response, context = Some(url))(Resource[MT]._reads)
      _ = eventHandler.handleUpdate(entityType -> id)
      _ <- invalidate(id)
    } yield item
  }

  override def promote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).post().flatMap(itemResponse(id, _))

  override def removePromotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "promote")).delete().flatMap(itemResponse(id, _))

  override def demote[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "demote")).post().flatMap(itemResponse(id, _))

  override def removeDemotion[MT: Resource](id: String): Future[MT] =
    userCall(enc(genericItemUrl, id, "demote")).delete().flatMap(itemResponse(id, _))

  override def links[A: Readable](id: String): Future[Page[A]] = {
    val pageParams = PageParams.empty.withoutLimit
    userCall(enc(genericItemUrl, id, "links")).withQueryString(pageParams.queryParams: _*)
      .get().map { response =>
      parsePage(response)(Readable[A]._reads)
    }
  }

  override def annotations[A: Readable](id: String): Future[Page[A]] = {
    val url = enc(genericItemUrl, id, "annotations")
    val pageParams = PageParams.empty.withoutLimit
    userCall(url).withQueryString(pageParams.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A]._reads)
    }
  }

  override def itemPermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]] =
    listWithUrl(enc(genericItemUrl, id, "permission-grants"), params)

  override def scopePermissionGrants[A: Readable](id: String, params: PageParams): Future[Page[A]] =
    listWithUrl(enc(genericItemUrl, id, "scope-permission-grants"), params)

  override def history[A: Readable](id: String, params: RangeParams,
    filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(genericItemUrl, id, "events")
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A]._reads), ec)
  }

  override def createAccessPoint[MT: Resource, AP: Writable](id: String, did: String, item: AP, logMsg: Option[String] = None): Future[AP] = {
    val url: String = enc(genericItemUrl, id, "descriptions", did, "access-points")
    val callF = userCall(url)
      .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(Writable[AP]._format))
    for {
      response <- callF
      item = checkErrorAndParse(response, context = Some(url))(Writable[AP]._format)
      _ = eventHandler.handleUpdate(Resource[MT].entityType -> id)
      _ <- invalidate(id)
    } yield item
  }

  override def deleteAccessPoint[MT: Resource](id: String, did: String, apid: String, logMsg: Option[String] = None): Future[Unit] = {
    val url = enc(genericItemUrl, id, "descriptions", did, "access-points", apid)
    for {
      response <- userCall(url).withHeaders(msgHeader(logMsg): _*).delete()
      _ = checkError(response)
      _ = eventHandler.handleUpdate(Resource[MT].entityType -> id)
      _ <- invalidate(id)
    } yield ()
  }

  override def userActions[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.UserProfile, userId, "actions")
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A]._reads), ec)
  }

  override def userEvents[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.UserProfile, userId, "events")
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A]._reads), ec)
  }


  override def versions[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(genericItemUrl, id, "versions")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*)
      .withHeaders(Ranged.streamHeader).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A]._reads)
    }
  }

  override def createAnnotation[A <: WithId : Readable, AF: Writable](id: String, ann: AF, accessors: Seq[String] =
  Nil, subItem: Option[String] = None): Future[A] = {
    val url: String = enc(typeBaseUrl, EntityType.Annotation)
    userCall(url)
      .withQueryString(TARGET_PARAM -> id)
      .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
      .withQueryString(subItem.toSeq.map(a => BODY_PARAM -> a): _*)
      .post(Json.toJson(ann)(Writable[AF]._format)).map { response =>
      val annotation: A = checkErrorAndParse[A](response, context = Some(url))(Readable[A]._reads)
      eventHandler.handleCreate(annotation.idAndType)
      annotation
    }
  }

  override def linkItems[MT: Resource, A <: WithId : Readable, AF: Writable](id: String, to: String, link: AF, accessPoint: Option[String] = None, directional: Boolean = false): Future[A] = {
    val url: String = enc(typeBaseUrl, EntityType.Link)
    val callF = userCall(url)
      .withQueryString(
        SOURCE_PARAM -> id,
        TARGET_PARAM -> to,
        "directional" -> directional.toString
      )
      .withQueryString(accessPoint.map(a => BODY_PARAM -> a).toSeq: _*)
      .post(Json.toJson(link)(Writable[AF]._format))
    for {
      response <- callF
      link = checkErrorAndParse[A](response, context = Some(url))(Readable[A]._reads)
      _= eventHandler.handleCreate(link.idAndType)
      _ <- invalidate(id)
    } yield link
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
    for {
      r <- done
      _ <- invalidate(id)
    } yield r
  }

  override def events[A: Readable](params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[Seq[A]]] = {
    val url: String = enc(typeBaseUrl, EntityType.SystemEvent)
    fetchRange(userCall(url, filters.toSeq()), params, Some(url))(Reads.seq(Readable[A]._reads), ec)
  }

  override def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(typeBaseUrl, EntityType.SystemEvent, id, "subjects")
    userCall(url)
      .withQueryString(params.queryParams: _*)
      .withHeaders(params.headers: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A]._reads)
    }
  }

  override def addReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] = {
    for {
      _ <- userCall(enc(typeBaseUrl, EntityType.VirtualUnit, vcId, "includes"))
        .withQueryString(ids.map(id => ID_PARAM -> id): _*).post()
      _ = eventHandler.handleUpdate(Resource[MT].entityType -> vcId)
      _ <- invalidate(vcId)
    } yield ()
  }

  override def deleteReferences[MT: Resource](vcId: String, ids: Seq[String]): Future[Unit] = {
    if (ids.isEmpty) immediate(())
    else for {
      _ <- userCall(enc(typeBaseUrl, EntityType.VirtualUnit, vcId, "includes"))
        .withQueryString(ids.map(id => ID_PARAM -> id): _*).delete()
      _ = eventHandler.handleUpdate(Resource[MT].entityType -> vcId)
      _ <- invalidate(vcId)
    } yield ()
  }

  override def moveReferences[MT: Resource](fromVc: String, toVc: String, ids: Seq[String]): Future[Unit] =
    if (ids.isEmpty) immediate(())
    else for {
      _ <- userCall(enc(typeBaseUrl, EntityType.VirtualUnit, fromVc, "includes", toVc))
        .withQueryString(ids.map(id => ID_PARAM -> id): _*).post()
      // Update both source and target sets in the index
      _ <- invalidate(toVc, fromVc)
      entityType = Resource[MT].entityType
      _ = eventHandler.handleUpdate(entityType -> toVc, entityType -> fromVc)
    } yield ()

  private val permissionRequestUrl = enc(baseUrl, "permissions")

  override def permissionGrants[A: Readable](userId: String, params: PageParams): Future[Page[A]] = {
    val url: String = enc(permissionRequestUrl, userId, "permission-grants")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A]._reads)
    }
  }

  override def globalPermissions(userId: String): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId)
    cache.getOrElseUpdate(url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setGlobalPermissions(userId: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId)
    for {
      response <- userCall(url).post(Json.toJson(data))
      gp = checkErrorAndParse[GlobalPermissionSet](response, context = Some(url))
      _ <- cache.set(url, gp, cacheTime)
    } yield gp
  }

  override def itemPermissions(userId: String, contentType: ContentTypes.Value, id: String): Future[ItemPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "item", id)
    cache.getOrElseUpdate(url, cacheTime) {
      userCall(url).get().map { response =>
        checkErrorAndParse(response, context = Some(url))(ItemPermissionSet._reads(contentType))
      }
    }
  }

  override def setItemPermissions(userId: String, contentType: ContentTypes.Value, id: String, data: Seq[String]): Future[ItemPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "item", id)
    for {
      response <- userCall(url).post(Json.toJson(data))
      ip = checkErrorAndParse(response, context = Some(url))(ItemPermissionSet._reads(contentType))
      _ <- cache.set(url, ip, cacheTime)
    } yield ip
  }

  override def scopePermissions(userId: String, id: String): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "scope", id)
    cache.getOrElseUpdate(url, cacheTime) {
      userCall(url).get()
        .map(r => checkErrorAndParse[GlobalPermissionSet](r, context = Some(url)))
    }
  }

  override def setScopePermissions(userId: String, id: String, data: Map[String, Seq[String]]): Future[GlobalPermissionSet] = {
    val url = enc(permissionRequestUrl, userId, "scope", id)
    for {
      response <- userCall(url).post(Json.toJson(data))
      gp = checkErrorAndParse[GlobalPermissionSet](response, context = Some(url))
      _ <- cache.set(url, gp, cacheTime)
    } yield gp
  }

  override def addGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit] = {
    val callF = userCall(enc(typeBaseUrl, EntityType.Group, groupId, userId)).post(Map[String, Seq[String]]())
    for {
      response <- callF
      _ = checkError(response)
      _ <- invalidate(userId, groupId)
      _ <- cache.remove(enc(permissionRequestUrl, userId))
    } yield ()
  }

  override def removeGroup[GT: Resource, UT: Resource](groupId: String, userId: String): Future[Unit] = {
    val callF = userCall(enc(typeBaseUrl, EntityType.Group, groupId, userId)).delete()
    for {
      response <- callF
      _ = checkError(response)
      _ <- invalidate(userId, groupId)
      _ <- cache.remove(enc(permissionRequestUrl, userId))
    } yield ()
  }

  private val userRequestUrl = enc(typeBaseUrl, EntityType.UserProfile)

  private def followingUrl(userId: String) = enc(userRequestUrl, userId, "following")

  private def watchingUrl(userId: String) = enc(userRequestUrl, userId, "watching")

  private def blockedUrl(userId: String) = enc(userRequestUrl, userId, "blocked")

  private def isFollowingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-following", otherId)

  private def isWatchingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-watching", otherId)

  private def isBlockingUrl(userId: String, otherId: String) = enc(userRequestUrl, userId, "is-blocking", otherId)

  override def follow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).post()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isFollowingUrl(userId, otherId), true, cacheTime)
      _ <- cache.remove(followingUrl(userId))
      _ <- cache.remove(itemCacheKey(userId))
    } yield ()
  }

  override def unfollow[U: Resource](userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(followingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isFollowingUrl(userId, otherId), false, cacheTime)
      _ <- cache.remove(followingUrl(userId))
      _ <- cache.remove(itemCacheKey(userId))
    } yield ()
  }

  override def isFollowing(userId: String, otherId: String): Future[Boolean] = {
    val url = isFollowingUrl(userId, otherId)
    cache.getOrElseUpdate(url, cacheTime) {
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
      parsePage(r, context = Some(url))(Readable[U]._reads)
    }
  }

  override def following[U: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[U]] = {
    val url: String = followingUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[U]._reads)
    }
  }

  override def watching[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "watching")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A]._reads)
    }
  }

  override def watch(userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).post()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isWatchingUrl(userId, otherId), true, cacheTime)
      _ <- cache.remove(watchingUrl(userId))
    } yield ()
  }

  override def unwatch(userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(watchingUrl(userId)).withQueryString(ID_PARAM -> otherId).delete()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isWatchingUrl(userId, otherId), false, cacheTime)
      _ <- cache.remove(watchingUrl(userId))
    } yield ()
  }

  override def isWatching(userId: String, otherId: String): Future[Boolean] = {
    val url = isWatchingUrl(userId, otherId)
    cache.getOrElseUpdate(url, cacheTime) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def blocked[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = blockedUrl(userId)
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A]._reads)
    }
  }

  override def block(userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).post()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isBlockingUrl(userId, otherId), true, cacheTime)
      _ <- cache.remove(blockedUrl(userId))
    } yield ()
  }

  override def unblock(userId: String, otherId: String): Future[Unit] = {
    val callF = userCall(blockedUrl(userId)).withQueryString(ID_PARAM -> otherId).delete()
    for {
      r <- callF
      _ = checkError(r)
      _ <- cache.set(isBlockingUrl(userId, otherId), false, cacheTime)
      _ <- cache.remove(blockedUrl(userId))
    } yield ()
  }

  override def isBlocking(userId: String, otherId: String): Future[Boolean] = {
    val url = isBlockingUrl(userId, otherId)
    cache.getOrElseUpdate(url, cacheTime) {
      userCall(url).get().map { r =>
        checkErrorAndParse[Boolean](r)
      }
    }
  }

  override def userAnnotations[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "annotations")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A]._reads)
    }
  }

  override def userLinks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "links")
    userCall(url).withQueryString(params.queryParams: _*).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A]._reads)
    }
  }

  override def userBookmarks[A: Readable](userId: String, params: PageParams = PageParams.empty): Future[Page[A]] = {
    val url: String = enc(userRequestUrl, userId, "virtual-units")
    userCall(url).get().map { r =>
      parsePage(r, context = Some(url))(Readable[A]._reads)
    }
  }

  private implicit val seqTuple2Format: Format[Seq[(String, String)]] = Format(
    Reads.seq[List[String]].map(_.collect { case a :: b :: _ => a -> b }),
    Writes { s => Json.toJson(s.map(t => Seq(t._1, t._2))) }
  )

  private implicit val seqTuple3Format: Format[Seq[(String, String, String)]] = Format(
    Reads.seq[List[String]].map(_.collect { case a :: b :: c :: _ => (a, b, c) }),
    Writes { s => Json.toJson(s.map(t => Seq(t._1, t._2, t._3))) }
  )

  override def relinkTargets(mapping: Seq[(String, String)], tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String, Int)]] = {
    val url = enc(baseUrl, "tools", "relink-targets")
    userCall(url)
      .withQueryString("tolerant" -> tolerant.toString)
      .withQueryString("commit" -> commit.toString)
      .post(Json.toJson(mapping))
      .map(r => checkErrorAndParse[Seq[(String, String, String)]](r, Some(url)))
      // Hack here: we get string numbers back from the backend for the total, so convert them to ints
      .map(_.map(r => r.copy(_3 = r._3.toInt)))
  }

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
      .withTimeout(config.get[Duration]("ehri.admin.bulkOperations.timeout"))
      .post()
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def regenerateIdsForScope(scope: String, tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "regenerate-ids-for-scope", scope)
    userCall(url).withQueryString("tolerant" -> tolerant.toString, "commit" -> commit.toString)
      .withTimeout(config.get[Duration]("ehri.admin.bulkOperations.timeout"))
      .post()
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def regenerateIds(ids: Seq[String], tolerant: Boolean = false, commit: Boolean = false): Future[Seq[(String, String)]] = {
    val url = enc(baseUrl, "tools", "regenerate-ids")
    userCall(url)
      .withQueryString("tolerant" -> tolerant.toString, "commit" -> commit.toString)
      .post(Json.toJson(ids.map(id => Seq(id))))
      .map(r => checkErrorAndParse[Seq[(String, String)]](r, Some(url)))
  }

  override def findReplace(ct: ContentTypes.Value, et: EntityType.Value, property: String, from: String, to: String, commit: Boolean, logMsg: Option[String]): Future[Seq[(String, String, String)]] = {
    val url = enc(baseUrl, "tools", "find-replace")
    val out = userCall(url)
      .withHeaders(msgHeader(logMsg): _*)
      .withQueryString("type" -> ct.toString, "subtype" -> et.toString,
        "property" -> property, "commit" -> commit.toString)
      .post(Map("from" -> Seq(from), "to" -> Seq(to)))
      .map(r => checkErrorAndParse[Seq[(String, String, String)]](r, Some(url)))
    out.map { list =>
      val toUpdate = list.map { case (id, _, _) => EntityType.withName(ct.toString) -> id }
      eventHandler.handleUpdate(toUpdate: _*)
      list
    }
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
    implicit val _writes: Writes[T] = Writable[T]._format
    val url = enc(baseUrl, "batch", "update")
    userCall(url).withQueryString(
      "version" -> version.toString,
      "commit" -> commit.toString,
      "log" -> logMsg)
      .withQueryString(scope.toSeq.map(s => "scope" -> s): _*)
      .put(Json.toJson(data))
      .map(r => checkErrorAndParse[BatchResult](r, Some(url)))
  }

  override def batchDelete(ids: Seq[String], scope: Option[String], logMsg: String, version: Boolean, tolerant: Boolean = false, commit: Boolean = false): Future[Int] = {
    val url = enc(baseUrl, "batch", "delete")
    val callF = userCall(url).withQueryString(
        "version" -> version.toString,
        "tolerant" -> tolerant.toString,
        "commit" -> commit.toString,
        "log" -> logMsg)
      .withTimeout(config.get[Duration]("ehri.admin.bulkOperations.timeout"))
      .withQueryString(scope.toSeq.map(s => "scope" -> s): _*)
      .post(Json.toJson(ids.map(id => Seq(id))))
      .map(r => checkErrorAndParse[Int](r, Some(url)))
    for (count <- callF; _ <- invalidate(ids)) yield count
  }

  // Helpers

  private def itemCacheKey(id: String) = s"item:$id"

  private def canonicalUrl[MT: Resource](id: String): String = enc(typeBaseUrl, Resource[MT].entityType, id)

  private def getTotal(url: String): Future[Long] =
    userCall(url).withMethod(HttpVerbs.HEAD).execute().map { response =>
      parsePagination(response, context = Some(url)).map { case (_, _, total) =>
        total.toLong
      }.getOrElse(-1L)
    }

  private def itemResponse[MT: Resource](id: String, response: WSResponse): Future[MT] = {
    val res = Resource[MT]
    val item: MT = checkErrorAndParse(response)(res._reads)
    eventHandler.handleUpdate(res.entityType -> id)
    invalidate(id).map(_ => item)
  }

  private def listWithUrl[A: Readable](url: String, params: PageParams, extra: Seq[(String,String)] = Seq.empty): Future[Page[A]] = {
    userCall(url).withQueryString(params.queryParams ++ extra: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[A]._reads)
    }
  }

  private def invalidate(id: String, rest: String*): Future[Done] = invalidate(id +: rest)

  private def invalidate(ids: Seq[String]): Future[Done] =
    Future.sequence(ids.map(id => cache.remove(itemCacheKey(id)))).map(_ => Done)

  private def streamWithUrl[A: Readable](url: String, extra: Seq[(String, String)] = Seq.empty): Source[A, _] = {
    val reader = implicitly[Readable[A]]
    Source.future(userCall(url)
      .withQueryString(PageParams.empty.withoutLimit.queryParams ++ extra: _*)
      .stream().map (_.bodyAsSource
      .via(JsonFraming.objectScanner(Integer.MAX_VALUE))
      .map { bytes => Json.parse(bytes.utf8String).validate[A](reader._reads) }
      .collect { case JsSuccess(item, _) => item}
    )).flatMapConcat(identity)
  }
}

object WsDataServiceBuilder {
  def withNoopHandler(cache: AsyncCacheApi, config: play.api.Configuration, ws: WSClient): DataServiceBuilder =
    new WsDataServiceBuilder(new EventHandler {
      def handleCreate(items: (EntityType.Value, String)*): Unit = ()

      def handleUpdate(items: (EntityType.Value, String)*): Unit = ()

      def handleDelete(items: (EntityType.Value, String)*): Unit = ()
    }, cache: AsyncCacheApi, config, ws)
}
