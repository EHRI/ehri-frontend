package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json._
import defines.{EntityType,ContentTypes}
import models.json.{RestResource, ClientConvertable, RestReadable, RestConvertable}
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import models.base.AnyModel
import utils.{PageParams,ListParams}

/**
 * Class representing a page of data.
 *
 * @param total
 * @param offset
 * @param limit
 * @param items
 * @tparam T
 */
case class Page[+T](
  total: Long,
  offset: Int,
  limit: Int,
  items: Seq[T]
) extends utils.AbstractPage[T]

object Page {

  implicit def restReads[T](implicit apiUser: ApiUser, rd: RestReadable[T]): Reads[Page[T]] = {
    Page.pageReads(rd.restReads)
  }
  implicit def clientFormat[T](implicit cfmt: ClientConvertable[T]): Writes[Page[T]] = {
    Page.pageWrites(cfmt.clientFormat)
  }

  import play.api.libs.json._
  import play.api.libs.json.util._
  import play.api.libs.functional.syntax._

  implicit def pageReads[T](implicit r: Reads[T]): Reads[Page[T]] = (
    (__ \ "total").read[Long] and
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int] and
    (__ \ "values").lazyRead(Reads.seq[T](r))
  )(Page.apply[T] _)

  implicit def pageWrites[T](implicit r: Writes[T]): Writes[Page[T]] = (
    (__ \ "total").write[Long] and
      (__ \ "offset").write[Int] and
      (__ \ "limit").write[Int] and
      (__ \ "values").lazyWrite(Writes.seq[T](r))
    )(unlift(Page.unapply[T] _))

  implicit def pageFormat[T](implicit r: Reads[T], w: Writes[T]): Format[Page[T]]
      = Format(pageReads(r), pageWrites(w))
}

trait RestEventHandler {
  def handleCreate(id: String): Unit
  def handleUpdate(id: String): Unit
  def handleDelete(id: String): Unit
}

/**
 * Data Access Object for fetching data about generic entity types.
 */
case class EntityDAO()(implicit eventHandler: RestEventHandler) extends RestDAO {

  import Constants._
  import play.api.http.Status._

  def requestUrl = "http://%s:%d/%s".format(host, port, mount)

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[MT] = {
    val cached = Cache.getAs[JsValue](id)
    if (cached.isDefined) {
      Future.successful(jsonReadToRestError(cached.get, rd.restReads))
    } else {
      val url = enc(requestUrl, entityType, id)
      userCall(url).get.map { response =>
        Cache.set(id, response.json, cacheTime)
        checkErrorAndParse(response)(rd.restReads)
      }
    }
  }

  def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT] = {
    get(rs.entityType, id)
  }

  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[JsObject] = {
    userCall(enc(requestUrl, rs.entityType, id)).get.map { response =>
      checkErrorAndParse[JsObject](response)
    }
  }

  def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[MT] = {
    userCall(enc(requestUrl, rs.entityType)).withQueryString("key" -> key, "value" -> value)
        .get.map { response =>
      checkErrorAndParse(response)(rd.restReads)
    }
  }

  def create[MT,T](item: T, accessors: List[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[MT] = {
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
        implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[TT] = {
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
      implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[MT] = {
    val url = enc(requestUrl, rs.entityType, id)
    userCall(url).withHeaders(msgHeader(logMsg): _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      val item = checkErrorAndParse(response)(rd.restReads)
      eventHandler.handleUpdate(id)
      Cache.remove(id)
      item
    }
  }

  def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Boolean] = {
    val url = enc(requestUrl, entityType, id)
    userCall(url).delete.map { response =>
      checkError(response)
      eventHandler.handleDelete(id)
      Cache.remove(id)
      true
    }
  }

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Boolean] = {
    delete(rs.entityType, id, logMsg)
  }

  def listJson[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[List[JsObject]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[List[JsObject]](response)
    }
  }

  def list[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[List[MT]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    userCall(url).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def listChildren[MT,CMT](id: String, params: ListParams = ListParams())(
      implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[List[CMT]] = {
    userCall(enc(requestUrl, rs.entityType, id, "list")).withQueryString(params.toSeq:_*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def pageJson[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Page[JsObject]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    userCall(url).withQueryString(params.toSeq:_*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads[JsObject])
    }
  }

  def page[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Page[MT]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    userCall(url).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def pageChildren[MT,CMT](id: String, params: PageParams = utils.PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[Page[CMT]] = {
    userCall(enc(requestUrl, rs.entityType, id, "page")).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def count[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, "count")).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  def countChildren[MT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Long] = {
    userCall(enc(requestUrl, rs.entityType, id, "count")).withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}
