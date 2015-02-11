package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import defines.ContentTypes
import play.api.cache.Cache
import utils.{Page, PageParams}
import backend._
import play.api.libs.json.JsObject
import scala.Some
import backend.ApiUser
import play.api.libs.json.JsObject


/**
 * Data Access Object for fetching data about generic entity types.
 */
trait RestGeneric extends Generic with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  override def get[MT](resource: BackendResource[MT], id: String)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[MT] = {
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

  override def get[MT](id: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    get(rs, id)
  }

  override def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[JsObject] = {
    val url: String = enc(baseUrl, rs.entityType, id)
    userCall(url).get().map { response =>
      checkErrorAndParse[JsObject](response, context = Some(url))
    }
  }

  override def create[MT <: WithId, T](item: T, accessors: Seq[String] = Nil,
      params: Map[String,Seq[String]] = Map.empty,
      logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], wrt: BackendWriteable[T], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(baseUrl, rs.entityType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(rs.restReads)
      eventHandler.handleCreate(created.id)
      created
    }
  }

  override def createInContext[MT,T,TT <: WithId](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty,
      logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, wrt: BackendWriteable[T], rs: BackendResource[MT], rd: backend.BackendReadable[TT], executionContext: ExecutionContext): Future[TT] = {
    val url = enc(baseUrl, rs.entityType, id, contentType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      // also reindex parent since this will update child count caches
      eventHandler.handleUpdate(id)
      eventHandler.handleCreate(created.id)
      created
    }
  }

  override def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, wrt: BackendWriteable[T], rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(baseUrl, rs.entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(rs.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      item
    }
  }

  override def patch[MT](id: String, data: JsObject, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[MT] = {
    val item = Json.obj(Entity.TYPE -> rs.entityType, Entity.DATA -> data)
    val url = enc(baseUrl, rs.entityType, id)
    userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
        .put(item).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(rs.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(canonicalUrl(id))
      item
    }
  }

  override def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit] = {
    userCall(enc(baseUrl, rs.entityType, id)).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      Cache.remove(canonicalUrl(id))
    }
  }

  override def listJson[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Page[JsObject]] = {
    val url = enc(baseUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.queryParams:_*).get().map { response =>
      parsePage[JsObject](response, context = Some(url))
    }
  }

  override def list[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Page[MT]] = {
    val url = enc(baseUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rs.restReads)
    }
  }

  override def listChildren[MT,CMT](id: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: backend.BackendReadable[CMT], executionContext: ExecutionContext): Future[Page[CMT]] = {
    val url: String = enc(baseUrl, rs.entityType, id, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  override def count[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(baseUrl, rs.entityType, "count")).withQueryString(params.queryParams: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  override def countChildren[MT](id: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(baseUrl, rs.entityType, id, "count")).withQueryString(params.queryParams: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}
