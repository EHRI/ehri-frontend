package backend.rest.cypher

import com.google.inject.Singleton
import play.api.cache.CacheApi

import scala.concurrent.Future
import play.api.{Logger, PlayException}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.libs.json.{Json,JsValue}
import play.api.libs.json.Reads
import play.api.libs.json.__
import play.api.libs.ws.{WSClient, WSResponse}
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

@Singleton
case class CypherDAO @Inject ()(
  implicit val cache: CacheApi,
  val config: play.api.Configuration,
  val ws: WSClient
) extends Cypher
  with RestDAO {

  def requestUrl = utils.serviceBaseUrl("cypher", config)

  import CypherErrorReader._

  private def checkCypherError(r: WSResponse): JsValue = r.json.validate[CypherError].fold(
    valid = err => throw err,
    invalid = e => r.json
  )

  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    Logger.logger.debug("Cypher: {}", Json.toJson(data))
    ws.url(requestUrl).withHeaders(headers.toSeq: _*).post(data).map(checkCypherError)
  }

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T] =
    cypher(scriptBody, params).map(_.as(implicitly[Reads[T]]))

  def stream(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[WSResponse] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    Logger.logger.debug("Cypher: {}", Json.toJson(data))
    ws.url(requestUrl).withHeaders((headers + ("X-Stream" -> "true")).toSeq: _*).post(data)
  }
}
