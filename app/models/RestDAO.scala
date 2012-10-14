package models

import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{ WS, Response }
import play.api.libs.json.{ JsArray, JsValue }
import play.api.Play

import com.codahale.jerkson.Json.generate

sealed trait RestError
case object PermissionDenied extends RestError
case object ValidationError extends RestError
case object ItemNotFound extends RestError
import com.codahale.jerkson.Json.generate

trait RestDAO {

  import play.api.http.Status._

  lazy val host: String = Play.current.configuration.getString("neo4j.server.host").get
  lazy val port: Int = Play.current.configuration.getInt("neo4j.server.port").get
  lazy val mount: String = Play.current.configuration.getString("neo4j.server.endpoint").get

  def jsonToEntity(js: JsValue): Entity = {
    EntityReader.entityReads.reads(js).fold(
      valid = { item =>
        item
      },
      invalid = { errors =>
        throw new RuntimeException("Error getting item: " + errors)
      })
  }

  protected def checkError(response: Response): Either[RestError, Response] = {
    //println("RESPONSE: " + response.body)
    response.status match {
      case OK | CREATED => Right(response)
      case UNAUTHORIZED => Left(PermissionDenied)
      case BAD_REQUEST => Left(ValidationError)
      case NOT_FOUND => Left(ItemNotFound)
      case _ => throw sys.error("Unexpected response: %d: %s".format(response.status, response.body))
    }
  }
}