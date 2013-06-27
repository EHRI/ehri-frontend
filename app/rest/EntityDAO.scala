package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json._
import defines.{EntityType,ContentType}
import models.{UserProfileMeta, Entity}
import models.json.{RestReadable, RestConvertable}
import play.api.Logger
import play.api.Play.current
import play.api.cache.Cache
import models.base.MetaModel

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

/**
 * Decode a JSON page representation.
 *
 */
object PageReads {

  import play.api.libs.json._
  import play.api.libs.json.util._
  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit def pageReads[T](implicit r: Reads[T]): Reads[Page[T]] = (
    (__ \ "total").read[Long] and
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int] and
    (__ \ "values").lazyRead(list[T](r))
  )(Page[T] _)
}

object RestPageParams {
  final val ACCESSOR_PARAM = "accessibleTo"
  final val ORDER_PARAM = "sort"
  final val FILTER_PARAM = "filter"
  final val OFFSET_PARAM = "offset"
  final val LIMIT_PARAM = "limit"
  final val PAGE_PARAM = "page"

  final val DEFAULT_LIST_LIMIT = 20
}

/**
 * Class for handling page parameter data
 * @param page
 * @param limit
 * @param filters
 * @param sort
 */
case class RestPageParams(page: Option[Int] = None, limit: Option[Int] = None, filters: List[String] = Nil, sort: List[String] = Nil) {

  import RestPageParams._

  def mergeWith(default: RestPageParams): RestPageParams = RestPageParams(
    page = page.orElse(default.page),
    limit = limit.orElse(default.limit),
    filters = if (filters.isEmpty) default.filters else filters,
    sort = if (sort.isEmpty) default.sort else sort
  )

  def offset: Int = (page.getOrElse(1) - 1) * limit.getOrElse(DEFAULT_LIST_LIMIT)
  def range: String = s"$offset-${offset + limit.getOrElse(DEFAULT_LIST_LIMIT)}"

  override def toString = {
    "?" + List(
      s"${OFFSET_PARAM}=${offset}",
      s"${LIMIT_PARAM}=${limit.getOrElse(DEFAULT_LIST_LIMIT)}",
      multiParam(FILTER_PARAM, filters),
      multiParam(ORDER_PARAM, sort)
    ).filterNot(_.trim.isEmpty).mkString("&")
  }

  private def multiParam(key: String, values: List[String]) = values.map(s => s"${key}=${s}").mkString("&")

}

object EntityDAO {
  implicit val entityReads = Entity.entityReads

  def jsonToEntity(js: JsValue): Entity = {
    js.validate.fold(
      valid = { item =>
        new Entity(item.id, item.`type`, item.data, item.relationships)
      },
      invalid = { errors =>
        sys.error("Error getting item: %s\n%s".format(errors, js))
      }
    )
  }

  /**
   * Global listeners for CUD events
   */
  import scala.collection.mutable.ListBuffer
  private val onCreate: ListBuffer[Entity => Unit] = ListBuffer()
  private val onUpdate: ListBuffer[Entity => Unit] = ListBuffer()
  private val onDelete: ListBuffer[String => Unit] = ListBuffer()

  def addCreateHandler(f: Entity => Unit): Unit = onCreate += f
  def addUpdateHandler(f: Entity => Unit): Unit = onUpdate += f
  def addDeleteHandler(f: String => Unit): Unit = onDelete += f

  def handleCreate(e: Entity): Entity = {
    onCreate.foreach(f => f(e))
    e
  }
  def handleUpdate(e: Entity): Entity = {
    onUpdate.foreach(f => f(e))
    e
  }
  def handleDelete(id: String): Unit = onDelete.foreach(f => f(id))
}

/**
 * Data Access Object for fetching data about generic entity types.
 *
 * @param entityType
 * @param userProfile
 */
case class EntityDAO(entityType: EntityType.Type, userProfile: Option[UserProfileMeta] = None) extends RestDAO {

  import EntityDAO._
  import play.api.http.Status._

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  def get[MT](id: String)(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    //val cached = Cache.getAs[MT](id)
    //if (cached.isDefined) Future.successful(Right(cached.get))
    //else {
      Logger.logger.debug("GET {} ", enc(requestUrl, id))
      WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
        checkError(response).right.map { r =>
          val entity = r.json.as[MT](rd.restReads)
          Cache.set(id, entity, cacheTime)
          entity
        }
      }
    //}
  }

  def getJson[TM](id: String)(implicit rw: RestReadable[TM]): Future[Either[RestError, JsResult[TM]]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[TM](rw.restReads)
      }
    }
  }

  def get[MT](key: String, value: String)(implicit rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
      .withQueryString("key" -> key, "value" -> value)
      .get.map { response =>
        checkError(response).right.map(r => r.json.as[MT](rd.restReads))
    }
  }

  def create[T, MT](item: T, accessors: List[String] = Nil,
      params: Map[String,Seq[String]] = Map(),
      logMsg: Option[String] = None)(implicit wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val qs = utils.joinQueryString(params)
    val url = enc(requestUrl, "?%s".format(
        (accessors.map(a => s"${RestPageParams.ACCESSOR_PARAM}=${a}") ++ List(qs)).mkString("&")))
    Logger.logger.debug("CREATE {} ", url)
    WS.url(url)
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
        checkError(response).right.map { r =>
          r.json.as[MT](rd.restReads)
          //EntityDAO.handleCreate(jsonToEntity(r.json))
        }
    }
  }

  def createInContext[T, MT](id: String, contentType: ContentType.Value,
      item: T, accessors: List[String] = Nil,
      logMsg: Option[String] = None)(implicit wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl, id, contentType, "?%s".format(
        accessors.map(a => s"${RestPageParams.ACCESSOR_PARAM}=${a}").mkString("&")))
    Logger.logger.debug("CREATE-IN {} ", url)
    WS.url(url)
        .withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .post(Json.toJson(item)(wrt.restFormat)).map { response =>
        checkError(response).right.map { r =>
          r.json.as[MT](rd.restReads)
          //EntityDAO.handleCreate(jsonToEntity(r.json))
        }
    }
  }

  def update[T, MT](id: String, item: T, logMsg: Option[String] = None)(
      implicit wrt: RestConvertable[T], rd: RestReadable[MT]): Future[Either[RestError, MT]] = {
    val url = enc(requestUrl, id)
    Logger.logger.debug("UPDATE: {}", url)
    WS.url(url).withHeaders(msgHeader(logMsg) ++ authHeaders.toSeq: _*)
      .put(Json.toJson(item)(wrt.restFormat)).map { response =>
        checkError(response).right.map { r =>
          //val entity = jsonToEntity(r.json)
          //EntityDAO.handleUpdate(entity)
          //Cache.set(id, entity, cacheTime)
          //entity
          r.json.as[MT](rd.restReads)
        }
    }
  }

  def delete(id: String, logMsg: Option[String] = None): Future[Either[RestError, Boolean]] = {
    val url = enc(requestUrl, id)
    Logger.logger.debug("DELETE {}", url)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => {
        EntityDAO.handleDelete(id)
        Cache.remove(id)
        r.status == OK
      })
    }
  }

  def list[MT](params: RestPageParams = RestPageParams())(implicit rd: RestReadable[MT]): Future[Either[RestError, List[MT]]] = {
    val url = enc(requestUrl, "list" + params.toString)
    Logger.logger.debug("LIST: {}", url)
    WS.url(url)
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[List[MT]](Reads.list(rd.restReads)).fold(
          valid = { list => list },
          invalid = { e =>
            sys.error(s"Unable to decode list result: $e: ${r.json}")
          }
        )
      }
    }
  }

  def listChildren[CMT](id: String, params: RestPageParams = RestPageParams())(
      implicit rd: RestReadable[CMT]): Future[Either[RestError, List[CMT]]] = {
    WS.url(enc(requestUrl, id, "list" + params.toString))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[List[CMT]](Reads.list(rd.restReads)).fold(
          valid = { list => list },
          invalid = { e =>
            sys.error(s"Unable to decode list result: $e: ${r.json}")
          }
        )
      }
    }
  }

  def page[MT](params: RestPageParams = RestPageParams())(implicit rd: RestReadable[MT]): Future[Either[RestError, Page[MT]]] = {
    val url = enc(requestUrl, "page" + params.toString)
    Logger.logger.debug("PAGE: {}", url)
    WS.url(url)
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[MT]](PageReads.pageReads(rd.restReads)).fold(
          valid = { page => page },
          invalid = { e =>
            sys.error(s"Unable to decode paginated list result: $e: ${r.json}")
          }
        )
      }
    }
  }

  def pageChildren[CMT](id: String, params: RestPageParams = RestPageParams())(implicit rd: RestReadable[CMT]): Future[Either[RestError, Page[CMT]]] = {
    implicit val entityPageReads = PageReads.pageReads[Entity]
    WS.url(enc(requestUrl, id, "page" + params.toString))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[CMT]](PageReads.pageReads(rd.restReads)).fold(
          valid = { page => page },
          invalid = { e =>
            sys.error(s"Unable to decode paginated list result: $e: ${r.json}")
          }
        )
      }
    }
  }

  def count(params: RestPageParams = RestPageParams()): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, "count" + params.toString))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => {
        r.json.validate[Long].fold(
          valid = { count => count },
          invalid = { e =>
            sys.error(s"Unable to decode count result: $e: ${r.json}")
          }
        )
      })
    }
  }

  def countChildren(id: String, params: RestPageParams = RestPageParams()): Future[Either[RestError, Long]] = {
    WS.url(enc(requestUrl, id, "count" + params.toString))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => {
        r.json.validate[Long].fold(
          valid = { count => count },
          invalid = { e =>
            sys.error(s"Unable to decode count result: $e: ${r.json}")
          }
        )
      })
    }
  }
}
