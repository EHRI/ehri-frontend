package rest

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{ JsArray, JsValue }
import defines.{EntityType,ContentType}
import models.Entity
import play.api.libs.json.Json
import models.UserProfile
import java.net.ConnectException
import models.base.Persistable

/**
 * Class representing a page of data.
 *
 * @param total
 * @param offset
 * @param limit
 * @param list
 * @tparam T
 */
case class Page[+T](val total: Long, val offset: Int, val limit: Int, val list: Seq[T]) {
  def numPages = (total / limit) + (total % limit).min(1)
  def page = (offset / limit) + 1

  def hasMultiplePages = total > limit
}

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

object EntityDAO {
  implicit val entityReads = Entity.entityReads

  final val ACCESSOR_PARAM = "accessibleTo"
  final val ORDER_PARAM = "sort"
  final val FILTER_PARAM = "filter"
  final val OFFSET_PARAM = "offset"
  final val LIMIT_PARAM = "limit"
  final val PAGE_PARAM = "page"

  final val DEFAULT_LIST_LIMIT = 20

  /**
   * Class for handling page parameter data
   * @param page
   * @param limit
   * @param filters
   * @param sort
   */
  case class PageData(page: Option[Int] = None, limit: Option[Int] = None, filters: List[String] = Nil, sort: List[String] = Nil) {
    override def toString = {
      val offset = (page.getOrElse(1) - 1) * limit.getOrElse(DEFAULT_LIST_LIMIT)
      "?" + List(
        s"${EntityDAO.OFFSET_PARAM}=${offset}",
        s"${EntityDAO.LIMIT_PARAM}=${limit.getOrElse(DEFAULT_LIST_LIMIT)}",
        multiParam(FILTER_PARAM, filters),
        multiParam(ORDER_PARAM, sort)
      ).filterNot(_.trim.isEmpty).mkString("&")
    }
  }

  /**
   * Class for handling list parameter data
   * @param offset
   * @param limit
   * @param filters
   * @param sort
   */
  case class ListData(offset: Option[Int] = None, limit: Option[Int] = None, filters: List[String] = Nil, sort: List[String] = Nil) {
    override def toString = {
      "?" + List(
        s"${OFFSET_PARAM}=${offset.getOrElse(0)}",
        s"${LIMIT_PARAM}=${limit.getOrElse(DEFAULT_LIST_LIMIT)}",
        multiParam(FILTER_PARAM, filters),
        multiParam(ORDER_PARAM, sort)
      ).mkString("&")
    }
  }

  private def multiParam(key: String, values: List[String]) = values.map(s => s"${key}=${s}").mkString("&")


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
}

/**
 * Data Access Object for fetching data about generic entity types.
 *
 * @param entityType
 * @param userProfile
 */
case class EntityDAO(val entityType: EntityType.Type, val userProfile: Option[UserProfile] = None) extends RestDAO {

  import EntityDAO._
  import play.api.http.Status._

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

  def get(id: String): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def get(key: String, value: String): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
      .withQueryString("key" -> key, "value" -> value)
      .get.map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def create(item: Persistable, accessors: List[String] = Nil): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, "?%s".format(accessors.map(a => s"${EntityDAO.ACCESSOR_PARAM}=${a}").mkString("&"))))
        .withHeaders(authHeaders.toSeq: _*)
      .post(item.toJson).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def createInContext(id: String, contentType: ContentType.Value, item: Persistable, accessors: List[String] = Nil): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, contentType, "?%s".format(accessors.map(a => s"${EntityDAO.ACCESSOR_PARAM}=${a}").mkString("&"))))
        .withHeaders(authHeaders.toSeq: _*)
      .post(item.toJson).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def update(id: String, item: Persistable): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .put(item.toJson).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def delete(id: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def list(params: ListData): Future[Either[RestError, Seq[Entity]]] = {
    WS.url(enc(requestUrl, "list", params.toString))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => sys.error("Unable to decode list result: " + r.json)
        }
      }
    }
  }

  def page(params: PageData): Future[Either[RestError, Page[Entity]]] = {
    import Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads
    WS.url(enc(requestUrl, "page", params.toString))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[models.Entity]].fold(
          valid = { page =>
            Page(page.total, page.offset, page.limit, page.list)
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }

  def listChildren(id: String, params: ListData): Future[Either[RestError, Seq[Entity]]] = {
    WS.url(enc(requestUrl, id, "list", params.toString))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => sys.error("Unable to decode list result: " + r.json)
        }
      }
    }
  }

  def pageChildren(id: String, params: PageData): Future[Either[RestError, Page[Entity]]] = {
    import Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads
    WS.url(enc(requestUrl, id, "page", params.toString))
      .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json.validate[Page[models.Entity]].fold(
          valid = { page =>
            Page(page.total, page.offset, page.limit, page.list)
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }

}