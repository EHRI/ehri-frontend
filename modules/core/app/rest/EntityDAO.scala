package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json._
import defines.{EntityType,ContentTypes}
import models.UserProfile
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

  def get[MT](entityType: EntityType.Value, id: String)(implicit apiUser: ApiUser, rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val cached = Cache.getAs[JsValue](id)
    if (cached.isDefined) {
      Future.successful(jsonReadToRestError(cached.get, rd.restReads))
    } else {
      val url = enc(requestUrl, entityType, id)
      Logger.logger.debug("GET {} ", url)
      WS.url(url).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkErrorAndParse(response)(rd.restReads).right.map { entity =>
          Cache.set(id, response.json, cacheTime)
          entity
        }
      }
    }
  }

  def get[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    get(rs.entityType, id)
  }

  def getJson[MT](id: String)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, JsObject]] = {
    WS.url(enc(requestUrl, rs.entityType, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[JsObject](response)
    }
  }

  def get[MT](key: String, value: String)(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(enc(requestUrl, rs.entityType)).withHeaders(authHeaders.toSeq: _*)
        .withQueryString("key" -> key, "value" -> value)
        .get.map { response =>
      checkErrorAndParse(response)(rd.restReads)
    }
  }

  def create[MT,T](item: T, accessors: List[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT], wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl, rs.entityType)
    Logger.logger.debug("CREATE {} ", url)
    WS.url(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withQueryString(unpack(params):_*)
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
        checkErrorAndParse(response)(rd.restReads).right.map { created =>
          created match {
            case item: AnyModel => eventHandler.handleCreate(item.id)
            case _ =>
          }
          created
        }
    }
  }

  def createInContext[MT,T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil,
      logMsg: Option[String] = None)(
        implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[TT]): Future[Either[RestError, TT]] = {
    val url = enc(requestUrl, rs.entityType, id, contentType)
    Logger.logger.debug("CREATE-IN {} ", url)
    WS.url(url)
        .withQueryString(accessors.map(a => ACCESSOR_PARAM -> a): _*)
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .post(Json.toJson(item)(wrt.restFormat)).map { response =>
      checkErrorAndParse(response)(rd.restReads).right.map { created =>
        created match {
          case item: AnyModel => eventHandler.handleCreate(item.id)
          case _ =>
        }
        created
      }
    }
  }

  def update[MT,T](id: String, item: T, logMsg: Option[String] = None)(
      implicit apiUser: ApiUser, wrt: RestConvertable[T], rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl, rs.entityType, id)
    Logger.logger.debug("UPDATE: {}", url)
    WS.url(url).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      checkErrorAndParse(response)(rd.restReads).right.map { item =>
        eventHandler.handleUpdate(id)
        Cache.remove(id)
        item
      }
    }
  }

  def delete[MT](entityType: EntityType.Value, id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser): Future[Either[RestError, Boolean]] = {
    val url = enc(requestUrl, entityType, id)
    Logger.logger.debug("DELETE {}", url)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => {
        eventHandler.handleDelete(id)
        Cache.remove(id)
        r.status == OK
      })
    }
  }

  def delete[MT](id: String, logMsg: Option[String] = None)(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, Boolean]] = {
    delete(rs.entityType, id, logMsg)
  }

  def listJson[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, List[JsObject]]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    Logger.logger.debug("LIST: {}", (url, params.toSeq))
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[List[JsObject]](response)
    }
  }

  def list[MT](params: ListParams = ListParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, List[MT]]] = {
    val url = enc(requestUrl, rs.entityType, "list")
    Logger.logger.debug("LIST: {}", url)
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def listChildren[MT,CMT](id: String, params: ListParams = ListParams())(
      implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[Either[RestError, List[CMT]]] = {
    WS.url(enc(requestUrl, rs.entityType, id, "list")).withQueryString(params.toSeq:_*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def pageJson[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, Page[JsObject]]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    Logger.logger.debug("PAGE: {}", url)
    WS.url(url).withQueryString(params.toSeq:_*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads[JsObject])
    }
  }

  def page[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[MT]): Future[Either[RestError, Page[MT]]] = {
    val url = enc(requestUrl, rs.entityType, "page")
    Logger.logger.debug("PAGE: {}", url)
    WS.url(url).withHeaders(authHeaders.toSeq: _*)
        .withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def pageChildren[MT,CMT](id: String, params: PageParams = utils.PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT], rd: RestReadable[CMT]): Future[Either[RestError, Page[CMT]]] = {
    WS.url(enc(requestUrl, rs.entityType, id, "page")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def count[MT](params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, rs.entityType, "count")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  def countChildren[MT](id: String, params: PageParams = PageParams())(implicit apiUser: ApiUser, rs: RestResource[MT]): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, rs.entityType, id, "count")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}
