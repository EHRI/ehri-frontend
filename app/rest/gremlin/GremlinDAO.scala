package rest.gremlin

import play.api.libs.ws.{ WS, Response }
import com.codahale.jerkson.Json._
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import rest.RestDAO

import play.api.PlayException

case class GremlinError(
  val message: String, val exception: String, val stacktrace: List[String]) extends PlayException("Gremlin Script Error: %s".format(exception), message)

object GremlinErrorReader {

  import play.api.libs.json.Reads._

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

  /**
   *  For as-yet-undetermined reasons that data coming back from Neo4j seems
   *  to be encoded as ISO-8859-1, so we need to convert it to UTF-8. Obvs.
   *  this problem should eventually be fixed at the source, rather than here.
   */
  def fixEncoding(s: String) = new String(s.getBytes("ISO-8859-1"), "UTF-8")

  /*
   * Enum for declaring direction of relationships.
   */
  object Direction extends Enumeration("inV", "outV") {
    type Direction = Value
    val In, Out = Value
  }

  val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json; charset=utf8"
  )

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