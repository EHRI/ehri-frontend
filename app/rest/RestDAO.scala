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
import play.api.http.HeaderNames
import play.api.http.ContentTypes

sealed trait RestError extends Throwable
case object PermissionDenied extends RestError
case object ValidationError extends RestError
case object DeserializationError extends RestError
case object IntegrityError extends RestError
case object ItemNotFound extends RestError


object RestDAO extends RestDAO


trait RestDAO {

  /**
   * Name of the header that passes that accessing user id to
   * the server.
   */
  val AUTH_HEADER_NAME = "Authorization"

  /**
   *  For as-yet-undetermined reasons that data coming back from Neo4j seems
   *  to be encoded as ISO-8859-1, so we need to convert it to UTF-8. Obvs.
   *  this problem should eventually be fixed at the source, rather than here.
   */
  def fixEncoding(s: String) = new String(s.getBytes("ISO-8859-1"), "UTF-8")

  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  val headers = Map(
    HeaderNames.ACCEPT -> ContentTypes.JSON,
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )  
  
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
      case e => e match {

        case UNAUTHORIZED => Left(PermissionDenied)
        case BAD_REQUEST => println(response.json) ; (response.json \ "error") match {
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
}