package rest

import models.Entity
import play.api.libs.json._
import play.api.Play
import play.api.http.HeaderNames
import play.api.http.ContentTypes
import scala.Left
import play.api.libs.ws.Response
import scala.Right
import scala.Some


sealed trait RestError extends Throwable
case class PermissionDenied() extends RestError
case class ValidationError(errorSet: ErrorSet) extends RestError
case class DeserializationError() extends RestError
case class IntegrityError() extends RestError
case class ItemNotFound() extends RestError
case class ServerError() extends RestError

object RestDAO extends RestDAO

/**
 * Structure that holds a set of errors for an entity and its
 * subtree relations.
 * 
 */
object ErrorSet {

  import play.api.libs.json.Reads._
  import play.api.libs.json.util._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._

  implicit val errorReads: Reads[ErrorSet] = (
    (__ \ "errors").lazyRead(map[List[String]]) and
    (__ \ Entity.RELATIONSHIPS).lazyRead(
      map[List[Option[ErrorSet]]](list(optionNoError(errorReads)))))(ErrorSet.apply _)


  def fromJson(json: JsValue): ErrorSet = json.validate[ErrorSet].fold(
    valid = { item => item },
    invalid = { e =>
      sys.error("Unable to parse error: " + json + " -> " + e)
    }
  )
}

case class ErrorSet(
  errors: Map[String,List[String]],
  relationships: Map[String,List[Option[ErrorSet]]]
) {

  /**
   * Given a persistable class, unfurl the nested errors so that they
   * conform to members of this class's form fields.
   */
  def errorsFor: Map[String,List[String]] = {
    // TODO: Handle nested errors
    errors
  }
}


trait RestDAO {

  /**
   * Name of the header that passes that accessing user id to
   * the server.
   */
  val AUTH_HEADER_NAME = "Authorization"

  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  val headers = Map(
    HeaderNames.ACCEPT -> ContentTypes.JSON,
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )  
  
  import play.api.http.Status._

  import java.net.URI

  def enc(s: Any*) = {
    val url = new java.net.URL(s.mkString("/"))
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

        case UNAUTHORIZED => Left(PermissionDenied())
        case BAD_REQUEST => response.json.validate[ErrorSet].fold(
          valid = { errorSet =>
            Left(ValidationError(errorSet))
          },
          invalid = { e =>
            // Temporary approach to handling random Deserialization errors.
            // In practice this should happen
            if ((response.json \ "error").asOpt[String] == Some("DeserializationError")) {
              println("GOT DESERIALIZATION ERROR: " + response.json)
              Left(DeserializationError())
            } else {
              throw sys.error("Unexpected BAD REQUEST: %s \n%s".format(e, response.body))
            }
          }
        )
        case NOT_FOUND => Left(ItemNotFound())
        case _ => {
          println(response.body)
          sys.error("Unexpected response: %d: %s".format(response.status, response.body))
        }
      }
    }
  }
}