package rest

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.libs.json.{JsArray,JsValue}
import models.AccessibleEntity
import models.EntityReader
import models.UserProfile
import play.api.http.Status.OK
import defines.EntityType

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
}