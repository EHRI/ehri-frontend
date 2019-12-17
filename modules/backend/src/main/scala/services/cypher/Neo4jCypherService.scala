package services.cypher

import akka.actor.ActorSystem
import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.Source
import akka.stream.{Materializer, scaladsl}
import akka.util.ByteString
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.cache.SyncCacheApi
import play.api.http.HttpVerbs
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsValue, Json, Reads, __}
import play.api.libs.ws.{WSClient, WSRequest}

import scala.concurrent.{ExecutionContext, Future}


private case class Neo4jCypherResultData(row: List[JsValue], meta: Seq[JsValue])

private object Neo4jCypherResultData {
  implicit val reads: Reads[Neo4jCypherResultData] = (
    (__ \ "row").read[List[JsValue]] and
    (__ \ "meta").read[Seq[JsValue]]
  )(Neo4jCypherResultData.apply _)
}

private case class Neo4jCypherResult(columns: Seq[String], data: Seq[Neo4jCypherResultData]) {
  def asLegacy: CypherResult = CypherResult(columns, data.map(_.row))
}

private object Neo4jCypherResult {
  implicit val reads: Reads[Neo4jCypherResult] = (
    (__ \ "columns").read[Seq[String]] and
    (__ \ "data").read[Seq[Neo4jCypherResultData]]
  )(Neo4jCypherResult.apply _)
}


@Singleton
case class Neo4jCypherService @Inject ()(
  ws: WSClient,
  cache: SyncCacheApi,
  config: play.api.Configuration)(
    implicit actorSystem: ActorSystem, mat: Materializer, executionContext: ExecutionContext)
  extends CypherService {

  val logger: Logger = play.api.Logger(getClass)

  private val requestUrl = utils.serviceBaseUrl("cypher", config)

  override def get(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[CypherResult] =
    raw(scriptBody, params).execute(HttpVerbs.POST)
      .map(r => (r.json \ "results" \ 0).as[Neo4jCypherResult].asLegacy)

  override def rows(scriptBody: String, params: Map[String,JsValue] = Map.empty): Source[List[JsValue], _] = {
    scaladsl.Source.future(raw(scriptBody, params).stream().map { sr =>
      sr.bodyAsSource
        .via(JsonReader.select("$.results[0].data[*].row"))
        .map { rowBytes =>
          Json.parse(rowBytes.toArray).as[List[JsValue]]
        }
    }).flatMapConcat(identity)
  }

  override def legacy(scriptBody: String, params: Map[String,JsValue] = Map.empty): Source[ByteString, _] = {
    val data = Json.obj(
      "query" -> scriptBody,
      "params" -> params
    )
    logger.debug(s"Legacy Cypher: ${Json.toJson(data)}")
    Source.future(
      ws.url(utils.serviceBaseUrl("legacyCypher", config))
        .withMethod(HttpVerbs.POST)
        .withHttpHeaders("X-Stream" -> "true")
        .withBody(data)
        .stream()
        .map(_.bodyAsSource))
    .flatMapConcat(identity)
  }

  private def raw(scriptBody: String, params: Map[String,JsValue] = Map.empty): WSRequest = {
    val data = Json.obj("statements" -> Json.arr(
        Json.obj(
      "statement" -> scriptBody,
            "parameters" -> params
        )
    ))
    logger.debug(s"Cypher: ${Json.toJson(data)}")
    ws.url(requestUrl)
      .withMethod(HttpVerbs.POST)
      .withHttpHeaders("X-Stream" -> "true")
      .withBody(data)
  }
}
