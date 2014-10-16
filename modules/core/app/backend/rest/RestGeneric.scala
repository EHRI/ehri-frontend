package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import defines.ContentTypes
import play.api.Play.current
import play.api.cache.Cache
import models.base.AnyModel
import utils.{Page, PageParams}
import backend._
import models.Entity
import scala.Some
import backend.ApiUser
import play.api.libs.json.JsObject


/**
 * Data Access Object for fetching data about generic entity types.
 */
trait RestGeneric extends Generic with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  def get[MT](resource: BackendResource[MT], id: String)(implicit apiUser: ApiUser, rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    Cache.getAs[JsValue](id) match {
      case Some(json) => Future.successful(jsonReadToRestError(json, rd.restReads))
      case _ =>
      val url = enc(requestUrl, resource.entityType, id)
      userCall(url, resource.defaultParams).get().map { response =>
        val item = checkErrorAndParse(response, context = Some(url))(rd.restReads)
        Cache.set(id, response.json, cacheTime)
        item
      }
    }
  }

  def get[MT](id: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    get(rs, id)
  }

  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[JsObject] = {
    val url: String = enc(requestUrl, rs.entityType, id)
    userCall(url).get().map { response =>
      checkErrorAndParse[JsObject](response, context = Some(url))
    }
  }

  def create[MT,T](item: T, accessors: Seq[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], wrt: BackendWriteable[T], rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(requestUrl, rs.entityType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      created match {
        case item: AnyModel => eventHandler.handleCreate(item.id); created
        case _ => created
      }
    }
  }

  def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: Seq[String] = Nil, params: Map[String, Seq[String]] = Map.empty,
      logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, wrt: BackendWriteable[T], rs: BackendResource[MT], rd: backend.BackendReadable[TT], executionContext: ExecutionContext): Future[TT] = {
    val url = enc(requestUrl, rs.entityType, id, contentType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      created match {
        case item: AnyModel =>
          // also reindex parent since this will update child count caches
          eventHandler.handleUpdate(id)
          eventHandler.handleCreate(item.id)
        case _ =>
      }
      created
    }
  }

  def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, wrt: BackendWriteable[T], rs: BackendResource[MT], rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(requestUrl, rs.entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      item
    }
  }

  def patch[MT](id: String, data: JsObject, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: BackendResource[MT], rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val item = Json.obj(Entity.TYPE -> rs.entityType, Entity.DATA -> data)
    val url = enc(requestUrl, rs.entityType, id)
    userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
        .put(item).map { response =>
      val item = checkErrorAndParse(response, context = Some(url))(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      item
    }
  }

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Unit] = {
    userCall(enc(requestUrl, rs.entityType, id)).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      Cache.remove(id)
    }
  }

  def listJson[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Page[JsObject]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.queryParams:_*).get().map { response =>
      parsePage[JsObject](response, context = Some(url))
    }
  }

  def list[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: backend.BackendReadable[MT], executionContext: ExecutionContext): Future[Page[MT]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def listChildren[MT,CMT](id: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], rd: backend.BackendReadable[CMT], executionContext: ExecutionContext): Future[Page[CMT]] = {
    val url: String = enc(requestUrl, rs.entityType, id, "list")
    userCall(url).withQueryString(params.queryParams: _*).get().map { response =>
      parsePage(response, context = Some(url))(rd.restReads)
    }
  }

  def count[MT](params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, "count")).withQueryString(params.queryParams: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  def countChildren[MT](id: String, params: PageParams = PageParams.empty)(implicit apiUser: ApiUser, rs: BackendResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, id, "count")).withQueryString(params.queryParams: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}

case class EntityDAO(eventHandler: EventHandler) extends RestGeneric
