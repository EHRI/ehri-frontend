package backend.rest.cypher

import play.api.cache.CacheApi

import scala.concurrent.Future
import play.api.{Logger, PlayException}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json,JsValue}
import play.api.libs.json.Reads
import play.api.libs.json.__
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WS
import backend.rest.RestDAO
import javax.inject.Inject

case class CypherError(
  message: String, exception: String, stacktrace: Seq[String]
) extends PlayException("Cypher Script Error: %s".format(exception), message)

object CypherErrorReader {

  import play.api.libs.json.Reads._
  import play.api.libs.functional.syntax._

  implicit val cypherErrorReads: Reads[CypherError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(CypherError)
}

object CypherDAO {
  import play.api.libs.json._
  val stringList: Reads[Seq[String]] =
    (__ \ "data").read[Seq[Seq[String]]].map(_.flatMap(_.headOption))
}

case class CypherDAO @Inject ()(implicit cache: CacheApi, app: play.api.Application) extends RestDAO {

  def requestUrl = s"http://$host:$port/db/data/cypher"

  import CypherErrorReader._

  def checkCypherError(r: WSResponse): JsValue = r.json.validate[CypherError].fold(
    valid = err => throw err,
    invalid = e => r.json
  )

  def cypher(scriptBody: String, params: Map[String,JsValue] = Map()): Future[JsValue] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    Logger.logger.debug("Cypher: {}", Json.toJson(data))
    WS.url(requestUrl).withHeaders(headers.toSeq: _*).post(data).map(checkCypherError)
  }

  def get[T](scriptBody: String, params: Map[String,JsValue])(implicit r: Reads[T]): Future[T] =
    cypher(scriptBody, params).map(_.as(r))

  def stream(scriptBody: String, params: Map[String,JsValue] = Map()): Future[WSResponse] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    Logger.logger.debug("Cypher: {}", Json.toJson(data))
    WS.url(requestUrl).withHeaders((headers + ("X-Stream" -> "true")).toSeq: _*).post(data)
  }
}
