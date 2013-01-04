package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{ JsArray, JsValue }
import defines.EntityType
import models.Entity
import play.api.libs.json.Json
import models.UserProfile
import java.net.ConnectException

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
}

/**
 * Decode a JSON page representation.
 *
 */
object PageReads {

  import play.api.libs.json._
  import play.api.libs.json.util._
  import play.api.libs.json.Reads._

  implicit def pageReads[T](implicit r: Reads[T]): Reads[Page[T]] = (
    (__ \ "total").read[Long] and
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int] and
    (__ \ "values").lazyRead(list[T](r))
  )(Page[T] _)
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
  import com.codahale.jerkson.Json.generate

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

  def get(id: Long): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def get(id: String): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def get(key: String, value: String): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl).withHeaders(authHeaders.toSeq: _*)
      .withQueryString("key" -> key, "value" -> value)
      .get.map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def create(data: Map[String, Any]): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl)).withHeaders(authHeaders.toSeq: _*)
      .post(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def createInContext(givenType: EntityType.Value, id: String, data: Map[String, Any]): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl, id, givenType)).withHeaders(authHeaders.toSeq: _*)
      .post(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def update(id: String, data: Map[String, Any]): Future[Either[RestError, Entity]] = {
    println("Sending data: " + generate(data))
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*)
      .put(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def delete(id: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl, id)).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def list(offset: Int, limit: Int): Future[Either[RestError, Seq[Entity]]] = {
    WS.url(enc(requestUrl, "list?offset=%d&limit=%d".format(offset, limit))).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        json(r) match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => sys.error("Unable to decode list result: " + json(r))
        }
      }
    }
  }

  def page(page: Int, limit: Int): Future[Either[RestError, Page[Entity]]] = {
    import Entity.entityReads
    implicit val entityPageReads = PageReads.pageReads
    WS.url(enc(requestUrl, "page?offset=%d&limit=%d".format((page-1)*limit, limit)))
        .withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        json(r).validate[Page[models.Entity]].fold(
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