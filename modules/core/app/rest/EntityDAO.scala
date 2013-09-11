package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json._
import defines.{EntityType,ContentTypes}
import models.{UserProfile, Entity}
import models.json.{ClientConvertable, RestReadable, RestConvertable}
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import models.base.{AnyModel, MetaModel}
import play.api.mvc.{AnyContent, Request}
import play.api.data.Form
import utils.ListParams

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

  implicit def restReads[T](implicit rd: RestReadable[T]): Reads[Page[T]] = {
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
 *
 * @param entityType
 * @param userProfile
 */
case class EntityDAO[MT](entityType: EntityType.Type, userProfile: Option[UserProfile] = None)(implicit eventHandler: RestEventHandler) extends RestDAO {

  import Constants._
  import play.api.http.Status._

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  private def unpack(m: Map[String,Seq[String]]): Seq[(String,String)]
      = m.map(ks => ks._2.map(s => ks._1 -> s)).flatten.toSeq

  def get(id: String)(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    //val cached = Cache.getAs[MT](id)
    //if (cached.isDefined) Future.successful(Right(cached.get))
    //else {
      Logger.logger.debug("GET {} ", enc(requestUrl, id))
      WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkErrorAndParse(response)(rd.restReads).right.map { entity =>
          //Cache.set(id, entity, cacheTime)
          entity
        }
      }
    //}
  }

  def getJson(id: String): Future[Either[RestError, JsObject]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[JsObject](response)
    }
  }

  def get(key: String, value: String)(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
        .withQueryString("key" -> key, "value" -> value)
        .get.map { response =>
      checkErrorAndParse(response)(rd.restReads)
    }
  }

  def create[T](item: T, accessors: List[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl)
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

  def createInContext[T,TT](id: String, contentType: ContentTypes.Value, item: T, accessors: List[String] = Nil,
      logMsg: Option[String] = None)(
        implicit wrt: RestConvertable[T], rd: RestReadable[TT]): Future[Either[RestError, TT]] = {
    val url = enc(requestUrl, id, contentType)
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

  def update[T](id: String, item: T, logMsg: Option[String] = None)(
      implicit wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl, id)
    Logger.logger.debug("UPDATE: {}", url)
    WS.url(url).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
        .put(Json.toJson(item)(wrt.restFormat)).map { response =>
      checkErrorAndParse(response)(rd.restReads).right.map { item =>
        eventHandler.handleUpdate(id)
        item
      }
    }
  }

  def delete(id: String, logMsg: Option[String] = None): Future[Either[RestError, Boolean]] = {
    val url = enc(requestUrl, id)
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

  def listJson(params: ListParams = ListParams()): Future[Either[RestError, List[JsObject]]] = {
    val url = enc(requestUrl, "list")
    Logger.logger.debug("LIST: {}", (url, params.toSeq))
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[List[JsObject]](response)
    }
  }

  def list(params: ListParams = ListParams())(implicit rd: RestReadable[MT]): Future[Either[RestError, List[MT]]] = {
    val url = enc(requestUrl, "list")
    Logger.logger.debug("LIST: {}", url)
    WS.url(url).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def listChildren[CMT](id: String, params: ListParams = ListParams())(
      implicit rd: RestReadable[CMT]): Future[Either[RestError, List[CMT]]] = {
    WS.url(enc(requestUrl, id, "list")).withQueryString(params.toSeq:_*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Reads.list(rd.restReads))
    }
  }

  def pageJson(params: ListParams = ListParams()): Future[Either[RestError, Page[JsObject]]] = {
    val url = enc(requestUrl, "page")
    Logger.logger.debug("PAGE: {}", url)
    WS.url(url).withQueryString(params.toSeq:_*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads[JsObject])
    }
  }

  def page(params: ListParams = ListParams())(implicit rd: RestReadable[MT]): Future[Either[RestError, Page[MT]]] = {
    val url = enc(requestUrl, "page")
    Logger.logger.debug("PAGE: {}", url)
    WS.url(url).withHeaders(authHeaders.toSeq: _*)
        .withQueryString(params.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def pageChildren[CMT](id: String, params: ListParams = utils.ListParams())(implicit rd: RestReadable[CMT]): Future[Either[RestError, Page[CMT]]] = {
    WS.url(enc(requestUrl, id, "page")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse(response)(Page.pageReads(rd.restReads))
    }
  }

  def count(params: ListParams = ListParams()): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, "count")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }

  def countChildren(id: String, params: ListParams = ListParams()): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, id, "count")).withQueryString(params.toSeq: _*)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkErrorAndParse[Long](response)
    }
  }
}
