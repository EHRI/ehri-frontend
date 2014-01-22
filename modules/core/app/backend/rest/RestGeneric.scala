package backend.rest

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.json._
import defines.{EntityType,ContentTypes}
import models.json.{RestResource, RestReadable, RestConvertable}
import play.api.Play.current
import play.api.cache.Cache
import models.base.AnyModel
import utils.{PageParams,ListParams}
import backend.{Generic, EventHandler, ApiUser, Page}
import play.api.http.Status
import models.Entity


/**
 * Data Access Object for fetching data about generic entity types.
 */
trait RestGeneric extends Generic with RestDAO {

  val eventHandler: EventHandler

  import Constants._

  private def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  def get[MT](resource: RestResource[MT], id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val cached = Cache.getAs[JsValue](id)
    if (cached.isDefined) {
      Future.successful(jsonReadToRestError(cached.get, rd.restReads))
    } else {
      val url = enc(requestUrl, resource.entityType, id)
      userCall(url, resource.defaultParams).get().map { response =>
        Cache.set(id, response.json, cacheTime)
        checkErrorAndParse(response)(rd.restReads)
      }
    }
  }

  def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    get(rs, id)
  }

  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[JsObject] = {
    userCall(enc(requestUrl, rs.entityType, id)).get().map { response =>
      checkErrorAndParse[JsObject](response)
    }
  }

  def create[MT,T](item: T, accessors: List[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(requestUrl, rs.entityType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg): _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response)(rd.restReads)
      created match {
        case item: AnyModel => eventHandler.handleCreate(item.id); created
        case _ => created
      }
    }
  }

  def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil,
      logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT], executionContext: ExecutionContext): Future[TT] = {
    val url = enc(requestUrl, rs.entityType, id, contentType)
    userCall(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withHeaders(msgHeader(logMsg): _*)
        .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      val created = checkErrorAndParse(response)(rd.restReads)
      created match {
        case item: AnyModel => eventHandler.handleCreate(item.id)
        case _ =>
      }
      created
    }
  }

  def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val url = enc(requestUrl, rs.entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      val item = checkErrorAndParse(response)(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      item
    }
  }

  def patch[MT](id: String, data: JsObject, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[MT] = {
    val item = Json.obj(Entity.TYPE -> rs.entityType, Entity.DATA -> data)
    val url = enc(requestUrl, rs.entityType, id)
    userCall(url).withHeaders((PATCH_HEADER_NAME -> true.toString) +: msgHeader(logMsg): _*)
        .put(item).map { response =>
      val item = checkErrorAndParse(response)(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      item
    }
  }

  def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Boolean] = {
    val url = enc(requestUrl, entityType, id)
    userCall(url).delete().map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      Cache.remove(id)
      response.status == Status.OK
    }
  }

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[Boolean] = {
    delete(rs.entityType, id, logMsg)
  }

  def listJson[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[List[JsObject]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse[List[JsObject]](response)
    }
  }

  def list[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[List[MT]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def listChildren[MT,CMT](id: String, params: ListParams = ListParams())(
      implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT], executionContext: ExecutionContext): Future[List[CMT]] = {
    userCall(enc(requestUrl, rs.entityType, id, "list")).withQueryString(params.toSeq:_*).get().map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def pageJson[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[Page[JsObject]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    userCall(url).withQueryString(params.toSeq:_*).get().map { response =>
      checkErrorAndParse(response)(Page.pageReads[JsObject])
    }
  }

  def page[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT], executionContext: ExecutionContext): Future[Page[MT]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    userCall(url).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def pageChildren[MT,CMT](id: String, params: PageParams = utils.PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT], executionContext: ExecutionContext): Future[Page[CMT]] = {
    userCall(enc(requestUrl, rs.entityType, id, "page")).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def count[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, "count")).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  def countChildren[MT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], executionContext: ExecutionContext): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, id, "count")).withQueryString(params.toSeq: _*).get().map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}

case class EntityDAO(eventHandler: EventHandler) extends RestGeneric