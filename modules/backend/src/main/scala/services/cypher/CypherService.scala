package services.cypher

import akka.stream.alpakka.json.scaladsl.JsonReader
import akka.stream.scaladsl.{Keep, Source}
import akka.util.ByteString
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import javax.inject.{Inject, Singleton}
import play.api.cache.SyncCacheApi
import play.api.http.HttpVerbs
import play.api.libs.json.{JsValue, Json, Reads, __, _}
import play.api.libs.ws.ahc.StreamedResponse
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.{Logger, PlayException}
import utils.CsvHelpers

import scala.concurrent.{ExecutionContext, Future}


case class CypherError(
  message: String, exception: String, stacktrace: Seq[String]
) extends PlayException(s"Cypher Script Error: $exception", message)

object CypherErrorReader {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.Reads._

  implicit val cypherErrorReads: Reads[CypherError] = (
    (__ \ "message").read[String] and
    (__ \ "exception").read[String] and
    (__ \ "stacktrace").lazyRead(list[String])
  )(CypherError)
}

case class ResultFormat(columns: Seq[String], data: Seq[Seq[JsValue]]) {
  /**
    * Convert Cypher JSON results to CSV, with nested arrays pipe-delimited.
    */
  def toCsv(sep: Char = ',', quote: Boolean = false): String =
    CsvHelpers.writeCsv(columns, data
      .map(_.collect(ResultFormat.jsToString).toArray), sep = sep)

  def toData: Seq[Seq[String]] = data.map(_.collect(ResultFormat.jsToString))
}
object ResultFormat {
  implicit val _reads: Reads[ResultFormat] = Json.reads[ResultFormat]

  def jsToString: PartialFunction[JsValue, String] = {
    case JsString(s) => s
    case JsNumber(i) => i.toString()
    case JsNull => ""
    case JsBoolean(b) => b.toString
    case list: JsArray => list.value.map(jsToString).mkString("|")
  }
}


object CypherResultAdaptor {
  def toCsv(r: StreamedResponse, sep: Char): Source[ByteString, _] = {
    import utils.CsvHelpers
    val csvFormat = CsvSchema.builder().setColumnSeparator(sep).setUseHeader(false)
    val writer = CsvHelpers.mapper.writer(csvFormat.build())
    r.bodyAsSource
      .via(JsonReader.select("$.data[*]"))
      .map { rowBytes =>
        Json.parse(rowBytes.toArray).as[Seq[JsValue]]
      }.map { row =>
      val cols: Seq[String] = row.collect(ResultFormat.jsToString)
      ByteString.fromArray(writer.writeValueAsBytes(cols.toArray))
    }.watchTermination()(Keep.right)
  }
}

@Singleton
case class CypherService @Inject ()(
  ws: WSClient,
  cache: SyncCacheApi,
  config: play.api.Configuration)(implicit val executionContext: ExecutionContext)
  extends Cypher {

  val logger: Logger = play.api.Logger(getClass)

  private val requestUrl = utils.serviceBaseUrl("cypher", config)

  import services.cypher.CypherErrorReader._

  private def checkCypherError(r: WSResponse): JsValue = r.json.validate[CypherError].fold(
    valid = err => throw err,
    invalid = e => r.json
  )

  def cypher(scriptBody: String, params: Map[String,JsValue] = Map.empty): Future[JsValue] = {
    val data = Json.obj("query" -> scriptBody, "params" -> params)
    logger.debug(s"Cypher: ${Json.toJson(data)}")
    ws.url(requestUrl).post(data).map(checkCypherError)
  }

  def get[T: Reads](scriptBody: String, params: Map[String,JsValue]): Future[T] =
    cypher(scriptBody, params).map(_.as(implicitly[Reads[T]]))

  def rows(scriptBody: String, params: Map[String,JsValue]): Future[Source[Seq[JsValue], _]] = {
    raw(scriptBody, params).map { sr =>
      sr.bodyAsSource
        .via(JsonReader.select("$.data[*]"))
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
      .withHttpHeaders("X-Stream" -> "true")
      .withBody(data)
      .stream()
  }
}
