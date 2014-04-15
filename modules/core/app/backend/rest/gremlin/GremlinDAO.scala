package backend.rest.gremlin

import play.api.libs.ws.{ WS, Response }
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json.{Json,JsValue}
import play.api.PlayException
import models.UserProfile
import backend.rest.RestDAO

case class GremlinError(
  message: String, exception: String, stacktrace: List[String]
) extends PlayException("Gremlin Script Error: %s".format(exception), message)

object GremlinErrorReader {

  import play.api.libs.json._
  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit val gremlinErrorReads: Reads[GremlinError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(GremlinError)
}

case class GremlinDAO(userProfile: Option[UserProfile]) extends RestDAO {

  import play.api.Play.current

  def requestUrl = "http://%s:%d/db/data/ext/GremlinPlugin/graphdb/execute_script".format(host, port)

  /**
   * Script validator.
   */
  val scripts = new ScriptSource()

  /*
   * Enum for declaring direction of relationships.
   */
  object Direction extends Enumeration {
    type Direction = Value
    val In = Value("inV")
    val Out = Value("outV")
  }

  import GremlinErrorReader._

  def checkGremlinError(r: Response): Either[GremlinError, JsValue] = r.json.validate[GremlinError].fold(
    valid = err => Left(err),
    invalid = e => Right(r.json)
  )

  def script(scriptName: String, params: Map[String, JsValue] = Map()): Future[Either[GremlinError, JsValue]] = {
    scripts.loadScript("groovy/gremlin.groovy")
    val scriptBody = scripts.get(scriptName)
    val data = Json.obj("script" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(data).map(checkGremlinError(_))
  }

  def gremlin(scriptBody: String, params: Map[String, JsValue] = Map()): Future[Either[GremlinError, JsValue]] = {
    val data = Json.obj("script" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(data).map(checkGremlinError(_))
  }
}