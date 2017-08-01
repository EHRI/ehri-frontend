package services.rest.cypher

import play.api.cache.SyncCacheApi

import scala.concurrent.{ExecutionContext, Future}
import play.api.{Logger, PlayException}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.json.Reads
import play.api.libs.json.__
import play.api.libs.ws.{WSClient, WSResponse}
import services.rest.RestService
import javax.inject.{Inject, Singleton}

import akka.stream.scaladsl.Source
import play.api.http.HttpVerbs
import utils.streams.JsonStream


case class CypherError(
  message: String, exception: String, stacktrace: Seq[String]
) extends PlayException(s"Cypher Script Error: $exception", message)

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
case class CypherService @Inject ()(
  ws: WSClient,
  cache: SyncCacheApi,
  config: play.api.Configuration)(implicit val executionContext: ExecutionContext)
  extends Cypher
  with RestService {

  val logger: Logger = play.api.Logger(getClass)

  private val requestUrl = utils.serviceBaseUrl("cypher", config)

  import services.rest.cypher.CypherErrorReader._

  private def checkCypherError(r: WSResponse): JsValue = r.json.validate[CypherError].fold(
    valid = err => throw err,
    invalid = e => r.json
  )

  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    logger.debug(s"Cypher: ${Json.toJson(data)}")
    ws.url(requestUrl).withHttpHeaders(headers.toSeq: _*).post(data).map(checkCypherError)
  }

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T] =
    cypher(scriptBody, params).map(_.as(implicitly[Reads[T]]))

  def rows(scriptBody: String, params: Map[String,JsValue]): Future[Source[Seq[JsValue], _]] = {
    raw(scriptBody, params).map { sr =>
      sr.bodyAsSource
        .via(JsonStream.items("data.item"))
        .map { rowBytes =>
          Json.parse(rowBytes.toArray).as[Seq[JsValue]]
        }
    }
  }

  def raw(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[WSResponse] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    logger.debug(s"Cypher: ${Json.toJson(data)}")
    ws.url(requestUrl)
      .withMethod(HttpVerbs.POST)
      .withHttpHeaders((headers + ("X-Stream" -> "true")).toSeq: _*)
      .withBody(data)
      .stream()
  }
}
