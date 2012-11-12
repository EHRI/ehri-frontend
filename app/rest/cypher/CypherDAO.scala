package rest.cypher

import play.api.libs.ws.{ WS, Response }
import com.codahale.jerkson.Json._
import play.api.libs.concurrent.execution.defaultContext
import scala.concurrent.Future
import play.api.libs.json.JsValue
import play.api.libs.json._
import play.api.libs.json.util._
import rest.RestDAO

import play.api.PlayException

case class CypherError(
  val message: String, val exception: String, val stacktrace: List[String]  
) extends PlayException("Cypher Script Error: %s".format(exception), message)

object CypherErrorReader {

  import play.api.libs.json.Reads._

  implicit val cypherErrorReads: Reads[CypherError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(CypherError)
}

case class CypherDAO() extends RestDAO {

  def requestUrl = "http://%s:%d/db/data/cypher".format(host, port)

  /**
   *  For as-yet-undetermined reasons that data coming back from Neo4j seems
   *  to be encoded as ISO-8859-1, so we need to convert it to UTF-8. Obvs.
   *  this problem should eventually be fixed at the source, rather than here.
   */
  def fixEncoding(s: String) = new String(s.getBytes("ISO-8859-1"), "UTF-8")

  val headers = Map(
    "Accept" -> "application/json",
    "Content-Type" -> "application/json; charset=utf8"
  )

  import CypherErrorReader._

  def checkCypherError(r: Response): Either[CypherError, JsValue] = r.json.validate[CypherError].fold(
    valid = err => Left(err),
    invalid = e => Right(r.json)
  )

  def cypher(scriptBody: String, params: Map[String,Any] = Map()): Future[Either[CypherError, JsValue]] = {
    val data = Map("query" -> scriptBody, "params" -> params)
    WS.url(requestUrl).withHeaders(headers.toList: _*).post(generate(data)).map(checkCypherError(_))
  }  
}