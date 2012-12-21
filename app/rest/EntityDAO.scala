package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{WS,Response => WSResponse}
import play.api.libs.json.{ JsArray, JsValue }
import models.base.AccessibleEntity
import models.EntityReader
import models.UserProfile
import play.api.http.Status.OK
import defines.EntityType
import models.Entity
import models.Entity
import play.api.libs.json.Json
import models.UserProfileRepr
import play.api.http.ContentTypes
import play.api.http.HeaderNames

case class Page[+T](val total: Long, val offset: Int, val limit: Int, val list: Seq[T]) {
  def numPages = (total / limit) + (total % limit).min(1)
  def page = (offset / limit) + 1 
}

object PageReads {

  import play.api.libs.json._
  import play.api.libs.json.util._
  import play.api.libs.json.Reads._
  import models.EntityReader._

  implicit def pageReads[T](r: Reads[T]): Reads[Page[T]] = (
    (__ \ "total").read[Long] and
    (__ \ "offset").read[Int] and
    (__ \ "limit").read[Int] and
    (__ \ "values").lazyRead(list[T](r))
  )(Page[T] _)
}

case class EntityDAO(val entityType: EntityType.Type, val userProfile: Option[UserProfileRepr] = None) extends RestDAO {

  import play.api.http.Status._
  import com.codahale.jerkson.Json.generate

  def jsonToEntity(js: JsValue): Entity = {
    EntityReader.entityReads.reads(js).fold(
      valid = { item =>
        new Entity(item.id, item.`type`, item.data, item.relationships)
      },
      invalid = { errors =>
        throw new RuntimeException("Error getting item: " + errors)
      })
  }

  /**
   *  For as-yet-undetermined reasons that data coming back from Neo4j seems
   *  to be encoded as ISO-8859-1, so we need to convert it to UTF-8. Obvs.
   *  this problem should eventually be fixed at the source, rather than here.
   *  NB: Fixed in Play 2.1 RC1
   */
  def fixEncoding(s: String) = new String(s.getBytes("ISO-8859-1"), "UTF-8")

  // Temporary solution until we upgrade to Play 2.1
  def json(r: WSResponse): JsValue = Json.parse(fixEncoding(r.body))

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

  def get(id: Long): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl + "/" + id.toString).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(json(r)))
    }
  }

  def get(id: String): Future[Either[RestError, Entity]] = {
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
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
    WS.url(enc("%s/%s/%s".format(requestUrl, id, givenType))).withHeaders(authHeaders.toSeq: _*)
      .post(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
      }
  }

  def update(id: String, data: Map[String, Any]): Future[Either[RestError, Entity]] = {
    println("Sending data: " + generate(data))
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders.toSeq: _*)
      .put(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(json(r)))
      }
  }

  def delete(id: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders.toSeq: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def list(offset: Int, limit: Int): Future[Either[RestError, Seq[Entity]]] = {
    WS.url(requestUrl + "/list?offset=%d&limit=%d".format(offset, limit)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        json(r) match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => throw new RuntimeException("Unexpected response to list...")
        }
      }
    }
  }

  def page(page: Int, limit: Int): Future[Either[RestError, Page[Entity]]] = {
    implicit val entityPageReads = PageReads.pageReads(models.EntityReader.entityReads)
    WS.url(requestUrl + "/page?offset=%d&limit=%d".format((page-1)*limit, limit)).withHeaders(authHeaders.toSeq: _*).get.map { response =>
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