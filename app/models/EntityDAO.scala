package models

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{ WS, Response }
import play.api.libs.json.{ JsArray, JsValue }
import play.api.Play

sealed trait RestError
case object PermissionDenied extends RestError
case object ValidationError extends RestError
case object ItemNotFound extends RestError
import com.codahale.jerkson.Json.generate

case class EntityDAO(val entityType: String) {

  lazy val host: String = Play.current.configuration.getString("neo4j.server.host").get
  lazy val port: Int = Play.current.configuration.getInt("neo4j.server.port").get
  lazy val mount: String = Play.current.configuration.getString("neo4j.server.endpoint").get

  def requestUrl = "http://%s:%d/%s/%s".format(host, port, mount, entityType)

  private val headers = Map(
      "Authorization" -> "21",
      "Content-Type" -> "application/json"
  )
  
  import play.api.http.Status._

  private def checkError(response: Response): Either[RestError, Response] = {
    response.status match {
      case OK|CREATED => Right(response)
      case UNAUTHORIZED => Left(PermissionDenied)
      case BAD_REQUEST => Left(ValidationError)
      case NOT_FOUND => Left(ItemNotFound)
      case _ => throw sys.error("Unexpected response: %d: %s".format(response.status, response.body))
    }
  }

  private def jsonToEntity(js: JsValue): Entity = {
    EntityReader.entityReads.reads(js).fold(
      valid = { item =>
        item
      },
      invalid = { errors =>
        throw new RuntimeException("Error getting item: " + errors)
      })
  }

  def get(id: Long): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl + "/" + id.toString).withHeaders(headers.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def get(id: String): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl + "/" + id).withHeaders(headers.toSeq: _*).get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def get(key: String, value: String): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl).withHeaders(headers.toSeq: _*)
    	.withQueryString("key" -> key, "value" -> value)
    	.get.map { response =>
      checkError(response).right.map(r => jsonToEntity(r.json))
    }
  }

  def create(data: Map[String, Object]): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl).withHeaders(headers.toSeq: _*)
      .post(generate(Map("id" -> None, "data" -> data))).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def update(id: Long, data: Map[String, Object]): Future[Either[RestError, Entity]] = {
    WS.url(requestUrl).withHeaders(headers.toSeq: _*)
      .put(generate(Map("id" -> id, "data" -> data))).map { response =>
        checkError(response).right.map(r => jsonToEntity(r.json))
      }
  }

  def delete(id: Long): Future[Either[RestError, Boolean]] = {
    WS.url(requestUrl + "/" + id.toString).withHeaders("Authorization" -> "21").delete.map { response =>
      // FIXME: Check actual error content...
      checkError(response).right.map(r => r.status == OK)
    }
  }

  def list: Future[Either[RestError, Seq[Entity]]] = {
    WS.url(requestUrl + "/list").withHeaders(headers.toSeq: _*).get.map { response =>
      checkError(response).right.map { r =>
        r.json match {
          case JsArray(array) => array.map(js => jsonToEntity(js))
          case _ => throw new RuntimeException("Unexpected response to list...")
        }
      }
    }
  }
}