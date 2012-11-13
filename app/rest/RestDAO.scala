package rest

import models.{ EntityReader, Entity }
import models.base.AccessibleEntity
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.ws.{ WS, Response }
import play.api.libs.json.{ JsArray, JsValue }
import play.api.Play
import com.codahale.jerkson.Json.generate
import play.api.libs.json.JsObject
import play.api.libs.json.JsString

sealed trait RestError extends Throwable
case object PermissionDenied extends RestError
case object ValidationError extends RestError
case object DeserializationError extends RestError
case object IntegrityError extends RestError
case object ItemNotFound extends RestError

trait RestDAO {

  import play.api.http.Status._

  import java.net.URI

  def enc(s: String) = {
    val url = new java.net.URL(s)
    val uri: URI = new URI(url.getProtocol, url.getUserInfo, url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef);
    uri.toString
  }

  lazy val host: String = Play.current.configuration.getString("neo4j.server.host").get
  lazy val port: Int = Play.current.configuration.getInt("neo4j.server.port").get
  lazy val mount: String = Play.current.configuration.getString("neo4j.server.endpoint").get

  protected def checkError(response: Response): Either[RestError, Response] = {
    //println("RESPONSE: " + response.body)
    response.status match {
      case OK | CREATED => Right(response)
      case UNAUTHORIZED => Left(PermissionDenied)
      case BAD_REQUEST => (response.json \ "error") match {
        case JsString("ValidationError") => Left(ValidationError)
        case JsString("DeserializationError") => Left(DeserializationError)
        case JsString("IntegrityError") => Left(IntegrityError)
        case _ => throw sys.error("Unexpected response: %d: %s".format(response.status, response.body))
      }
      case NOT_FOUND => Left(ItemNotFound)
      case _ => {
        println(response.body)
        sys.error("Unexpected response: %d: %s".format(response.status, response.body))
      }
    }
  }
}