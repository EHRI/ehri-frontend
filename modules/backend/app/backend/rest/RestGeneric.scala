package backend.rest

import scala.concurrent.Future
import play.api.libs.json._
import defines.ContentTypes
import play.api.cache.Cache
import utils.{Page, PageParams}
import backend._
import play.api.libs.json.JsObject


/**
 * Data Access Object for fetching data about generic entity types.
 */
trait RestGeneric extends Generic with RestDAO with RestContext {

  import Constants._

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  override def get[MT](resource: Resource[MT], id: String): Future[MT] = {
    val url = canonicalUrl(id)(resource)
    Cache.getAs[JsValue](url).map { json =>
      Future.successful(jsonReadToRestError(json, resource.restReads))
    }.getOrElse {
      userCall(url, resource.defaultParams).get().map { response =>
        val item = checkErrorAndParse(response, context = Some(url))(resource.restReads)
        Cache.set(url, response.json, cacheTime)
        item
      }
    }
  }

  override def get[MT: Resource](id: String): Future[MT] = {
    get(Resource[MT], id)
  }

  override def create[MT <: WithId : Resource, T: Writable](item: T, accessors: Seq[String] = Nil,
      params: Map[String,Seq[String]] = Map.empty, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(baseUrl, Resource[MT].entityType)
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

  override def createInContext[MT: Resource, T: Writable, TT <: WithId : Readable](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty,
      logMsg: Option[String] = None): Future[TT] = {
    val url = enc(baseUrl, Resource[MT].entityType, id, contentType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(Readable[TT].restReads)
      // also reindex parent since this will update child count caches
      eventHandler.handleUpdate(id)
      eventHandler.handleCreate(created.id)
      created
    }
  }

  override def update[MT: Resource, T: Writable](id: String, item: T, logMsg: Option[String] = None): Future[MT] = {
    val url = enc(baseUrl, Resource[MT].entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(Writable[T].restFormat)).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      item
    }
  }

  override def patch[MT: Resource](id: String, data: JsObject, logMsg: Option[String] = None): Future[MT] = {
    val item = Json.obj(Entity.TYPE -> Resource[MT].entityType, Entity.DATA -> data)
    val url = enc(baseUrl, Resource[MT].entityType, id)
    userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
        .put(item).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(Resource[MT].restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      item
    }
  }

  override def delete[MT: Resource](id: String, logMsg: Option[String] = None): Future[Unit] = {
    userCall(enc(baseUrl, Resource[MT].entityType, id)).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      Cache.remove(canonicalUrl(id))
    }
  }

  override def list[MT: Resource](params: PageParams = PageParams.empty): Future[Page[MT]] =
    list(Resource[MT], params)

  override def list[MT](resource: Resource[MT], params: PageParams = PageParams.empty): Future[Page[MT]] = {
    val url = enc(baseUrl, resource.entityType, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(resource.restReads)
    }
  }

  override def listChildren[MT: Resource, CMT: Readable](id: String, params: PageParams = PageParams.empty): Future[Page[CMT]] = {
    val url: String = enc(baseUrl, Resource[MT].entityType, id, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(Readable[CMT].restReads)
    }
  }

  override def count[MT: Resource](): Future[Long] = {
    userCall(enc(baseUrl, Resource[MT].entityType, "count")).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  override def countChildren[MT: Resource](id: String): Future[Long] = {
    userCall(enc(baseUrl, Resource[MT].entityType, id, "count")).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}
