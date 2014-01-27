package backend.rest

import play.api.{Logger, Play}
import play.api.http.HeaderNames
import play.api.http.ContentTypes
import play.api.libs.ws.{WS, Response}
import play.api.libs.json._
import play.api.libs.ws.WS.WSRequestHolder
import backend.{ErrorSet, ApiUser}
import com.fasterxml.jackson.core.JsonParseException


trait RestDAO {

  import Constants._
  import play.api.http.Status._

  /**
   * Header to add for log messages.
   */
  def msgHeader(msg: Option[String]): Seq[(String,String)] = msg.map(m => Seq(LOG_MESSAGE_HEADER_NAME -> m)).getOrElse(Seq[(String,String)]())

  /**
   * Join params into a query string
   */
  def joinQueryString(qs: Map[String, Seq[String]]): String = {
    import java.net.URLEncoder
    qs.map { case (key, vals) => {
      vals.map(v => "%s=%s".format(key, URLEncoder.encode(v, "UTF-8")))
    }}.flatten.mkString("&")
  }

  /**
   * Standard headers we sent to every Neo4j/EHRI Server request.
   */
  val headers = Map(
    HeaderNames.ACCEPT -> ContentTypes.JSON,
    HeaderNames.CONTENT_TYPE -> ContentTypes.JSON
  )

  /**
   * Headers to add to outgoing request...
   * @return
   */
  def authHeaders(implicit apiUser: ApiUser): Map[String, String] = apiUser.id match {
    case Some(id) => headers + (AUTH_HEADER_NAME -> id)
    case None => headers
  }

  /**
   * Create a web request with correct auth parameters for the REST API.
   */
  def userCall(url: String, params: Seq[(String,String)] = Seq.empty)(implicit apiUser: ApiUser): WSRequestHolder = {
    Logger.logger.debug("[{} {}] {}", apiUser, this.getClass.getCanonicalName, url)
    WS.url(url).withHeaders(authHeaders.toSeq: _*).withQueryString(params: _*)
  }

  /**
   * Encode a bunch of URL parts.
   */
  def enc(s: Any*) = {
    import java.net.URI
    val url = new java.net.URL(s.mkString("/"))
    val uri: URI = new URI(url.getProtocol, url.getUserInfo, url.getHost, url.getPort, url.getPath, url.getQuery, url.getRef)
    uri.toString
  }

  lazy val host: String = Play.current.configuration.getString("neo4j.server.host").get
  lazy val port: Int = Play.current.configuration.getInt("neo4j.server.port").get
  lazy val mount: String = Play.current.configuration.getString("neo4j.server.endpoint").get

  protected def checkError(response: Response): Response = {
    Logger.logger.trace("Response body ! : {}", response.body)
    response.status match {
      case OK | CREATED => response
      case e => e match {

        case UNAUTHORIZED => {
          response.json.validate[PermissionDenied].fold(
            err => throw PermissionDenied(),
            perm => {
              Logger.logger.error("Permission denied error! : {}", response.json)
              throw perm
            }
          )
        }
        case BAD_REQUEST => try {
          response.json.validate[ErrorSet].fold(
            e => {
              // Temporary approach to handling random Deserialization errors.
              // In practice this should happen
              if ((response.json \ "error").asOpt[String] == Some("DeserializationError")) {
                Logger.logger.error("Derialization error! : {}", response.json)
                throw DeserializationError()
              } else {
                Logger.error("Bad request: " + response.body)
                throw sys.error(s"Unexpected BAD REQUEST: $e \n${response.body}")
              }
            },
            errorSet => {
              Logger.logger.warn("ValidationError ! : {}", response.json)
              throw ValidationError(errorSet)
            }
          )
        } catch {
          case e: JsonParseException => {
            Logger.error(response.body)
            throw new BadRequest(response.body)
          }
        }
        case NOT_FOUND => {
          Logger.logger.error("404: {} -> {}", Array(response.ahcResponse.getUri, response.body))
          response.json.validate[ItemNotFound].fold(
            e => throw new ItemNotFound(),
            err => throw err
          )
        }
        case _ => {
          Logger.logger.error(response.body)
          sys.error(response.body)
        }
      }
    }
  }

  private[rest] def checkErrorAndParse[T](response: Response)(implicit reader: Reads[T]): T = {
    jsonReadToRestError(checkError(response).json, reader)
  }

  private[rest] def jsonReadToRestError[T](json: JsValue, reader: Reads[T]): T = {
    json.validate(reader).fold(
      invalid => {
        Logger.error("Bad JSON: " + invalid)
        throw new BadJson(invalid)
      },
      valid => valid
    )
  }
}