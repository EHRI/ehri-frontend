package rest.gremlin

import play.api.libs.ws.{ WS, Response }
import com.codahale.jerkson.Json._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import rest.RestDAO
import play.api.PlayException
import play.api.http.HeaderNames
import play.api.http.ContentTypes

case class GremlinError(
  val message: String, val exception: String, val stacktrace: List[String]) extends PlayException("Gremlin Script Error: %s".format(exception), message)

object GremlinErrorReader {

  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit val gremlinErrorReads: Reads[GremlinError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(GremlinError)
}

case class GremlinDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/db/data/ext/GremlinPlugin/graphdb/execute_script".format(host, port)

  /**
   * Script validator.
   */
  val scripts = new ScriptSource()

  /*
   * Enum for declaring direction of relationships.
   */
  object Direction extends Enumeration("inV", "outV") {
    type Direction = Value
    val In, Out = Value
  }

  import GremlinErrorReader._

  def checkGremlinError(r: Response): Either[GremlinError, JsValue] = r.json.validate[GremlinError].fold(
    valid = err => Left(err),
    invalid = e => Right(r.json)
  )

  def script(scriptName: String, params: Map[String, Any] = Map()): Future[Either[GremlinError, JsValue]] = {
    scripts.loadScript("groovy/gremlin.groovy")
    val scriptBody = scripts.get(scriptName)
    val data = Map("script" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(generate(data)).map(checkGremlinError(_))
  }

  def gremlin(scriptBody: String, params: Map[String, Any] = Map()): Future[Either[GremlinError, JsValue]] = {
    val data = Map("script" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(generate(data)).map(checkGremlinError(_))
  }
}