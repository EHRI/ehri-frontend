package rest

import models.{UserProfile, Entity}
import play.api.libs.json._
import play.api.{Logger, Play}
import play.api.http.HeaderNames
import play.api.http.ContentTypes
import play.api.libs.ws.Response


sealed trait RestError extends Throwable
case class PermissionDenied(
  user: Option[String] = None,
  permission: Option[String] = None,
  item: Option[String] = None,
  scope: Option[String] = None
) extends RestError
case class ValidationError(errorSet: ErrorSet) extends RestError
case class DeserializationError() extends RestError
case class IntegrityError() extends RestError
case class ItemNotFound() extends RestError
case class ServerError(error: String) extends RestError
case class CriticalError(error: String) extends RestError

object PermissionDenied {
  import play.api.libs.json.util._
  import play.api.libs.json._
  import play.api.libs.functional.syntax._
  implicit val permissionDeniedReads: Reads[PermissionDenied] = (
    (__ \ "details" \ "accessor").readNullable[String] and
    (__ \ "details" \ "permission").readNullable[String] and
    (__ \ "details" \ "item").readNullable[String] and
    (__ \ "details" \ "scope").readNullable[String]
  )(PermissionDenied.apply _)
}


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
  private final val LOG_MESSAGE_HEADER_NAME = "logMessage"

  /**
   * Time to cache rest requests for...
   * @param msg
   * @return
   */
  val cacheTime = 60 * 5 // 5 minutes

  def msgHeader(msg: Option[String]): Seq[(String,String)] = msg.map(m => Seq(LOG_MESSAGE_HEADER_NAME -> m)).getOrElse(Seq[(String,String)]())


  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  val headers = Map(
    HeaderNames.ACCEPT -> ContentTypes.JSON,
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )

  // Abstract value for the user accessing a resource...
  val userProfile: Option[UserProfile]

  /**
   * Headers to add to outgoing request...
   * @return
   */
  def authHeaders: Map[String, String] = userProfile match {
    case Some(up) => (headers + (AUTH_HEADER_NAME -> up.id))
    case None => headers
  }

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
    Logger.logger.trace("Response body ! : {}", response.body)
    response.status match {
      case OK | CREATED => Right(response)
      case e => e match {

        case UNAUTHORIZED => response.json.validate[PermissionDenied].fold(
          valid = { perm =>
            Logger.logger.error("Permission denied error! : {}", response.json)
            Left(perm)
          },
          invalid = { e =>
            Left(PermissionDenied())
          }
        )
        case BAD_REQUEST => response.json.validate[ErrorSet].fold(
          valid = { errorSet =>
            Logger.logger.error("ValidationError ! : {}", response.json)
            Left(ValidationError(errorSet))
          },
          invalid = { e =>
            // Temporary approach to handling random Deserialization errors.
            // In practice this should happen
            if ((response.json \ "error").asOpt[String] == Some("DeserializationError")) {
              Logger.logger.error("Derialization error! : {}", response.json)
              Left(DeserializationError())
            } else {
              throw sys.error(s"Unexpected BAD REQUEST: ${e} \n${response.body}")
            }
          }
        )
        case NOT_FOUND => Logger.logger.error("404: {} -> {}", Array(
          response.ahcResponse.getUri, response.body)); Left(ItemNotFound())
        case _ => {
          Logger.logger.error(response.body)
          sys.error(response.body)
        }
      }
    }
  }
}