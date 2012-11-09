package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json.{ JsArray, JsValue }
import models.AccessibleEntity
import models.EntityReader
import models.UserProfile
import play.api.http.Status.OK
import defines.EntityType

import models.Entity

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

case class EntityDAO(val entityType: EntityType.Type, val userProfile: Option[UserProfile] = None) extends RestDAO {

  import play.api.http.Status._
  import com.codahale.jerkson.Json.generate

  def jsonToEntity(js: JsValue): AccessibleEntity = {
    EntityReader.entityReads.reads(js).fold(
      valid = { item =>
        new AccessibleEntity(item.id, item.data, item.relationships)
      },
      invalid = { errors =>
        throw new RuntimeException("Error getting item: " + errors)
      })
  }

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  private val headers: Map[String, String] = Map(
    "Content-Type" -> "application/json"
  )

  def authHeaders: Seq[(String, String)] = userProfile match {
    case Some(up) => (headers + ("Authorization" -> up.identifier)).toSeq
    case None => headers.toSeq
  }

  def get(id: Long): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(requestUrl + "/" + id.toString).withHeaders(authHeaders: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def get(id: String): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def get(key: String, value: String): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(requestUrl).withHeaders(authHeaders: _*)
      .withQueryString("key" -> key, "value" -> value)
      .get.map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def create(data: Map[String, Any]): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(requestUrl).withHeaders(authHeaders: _*)
      .post(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def createInContext(givenType: EntityType.Value, id: String, data: Map[String, Any]): Future[Either[RestError, AccessibleEntity]] = {
    val requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType, id, givenType)
    WS.url(requestUrl).withHeaders(authHeaders: _*)
      .post(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def update(id: Long, data: Map[String, Any]): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(requestUrl).withHeaders(authHeaders: _*)
      .put(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def update(id: String, data: Map[String, Any]): Future[Either[RestError, AccessibleEntity]] = {
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders: _*)
      .put(generate(data)).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def delete(id: String): Future[Either[RestError, Boolean]] = {
    WS.url(enc(requestUrl + "/" + id)).withHeaders(authHeaders: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def delete(id: Long): Future[Either[RestError, Boolean]] = {
    WS.url(requestUrl + "/" + id.toString).withHeaders(authHeaders: _*).delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def list(offset: Int, limit: Int): Future[Either[RestError, Seq[AccessibleEntity]]] = {
    WS.url(requestUrl + "/list?offset=%d&limit=%d".format(offset, limit)).withHeaders(authHeaders: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => throw new RuntimeException("Unexpected response to list...")
        }
      }
    }
  }

  def page(page: Int, limit: Int): Future[Either[RestError, Page[AccessibleEntity]]] = {
    println("PAGE: " + page)
    implicit val entityPageReads = PageReads.pageReads(models.EntityReader.entityReads)
    WS.url(requestUrl + "/page?offset=%d&limit=%d".format((page-1)*limit, limit)).withHeaders(authHeaders: _*).get.map { response =>
      checkError(response).right.map { r =>
        println(r.json)
        r.json.validate[Page[models.Entity]].fold(
          valid = { page => 
            Page(page.total, page.offset, page.limit, page.list.map(e => new AccessibleEntity(e)))            
          },
          invalid = { e =>
            sys.error("Unable to decode paginated list result: " + e.toString)
          }
        )
      }
    }
  }
}