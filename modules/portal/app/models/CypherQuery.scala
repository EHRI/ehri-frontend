package models

import backend.rest.cypher.CypherDAO
import org.joda.time.DateTime
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.iteratee.Enumerator
import play.api.libs.json._
import play.api.libs.ws.WSResponseHeaders
import utils.CsvHelpers

import scala.concurrent.{ExecutionContext, Future}

case class ResultFormat(columns: Seq[String], data: Seq[Seq[JsValue]]) {
  def toCsv(quote: Boolean = false): String =
    CsvHelpers.writeCsv(columns, data.map(_.collect {
      case JsString(s) => s
      case JsNumber(i) => i.toString()
      case JsNull => ""
      case JsBoolean(b) => b.toString
    }.toArray), quote)
}
object ResultFormat {
  implicit val _reads = Json.reads[ResultFormat]
}


/**
 * A pre-baked Cypher query.
 */
case class CypherQuery(
  objectId: Option[String] = None,
  userId: Option[String] = None,
  name: String,
  query: String,
  description: Option[String] = None,
  createdAt: Option[DateTime] = None,
  updatedAt: Option[DateTime] = None
) {
  def download(implicit cypher: CypherDAO, executionContext: ExecutionContext): Future[(WSResponseHeaders, Enumerator[Array[Byte]])] =
    cypher.stream(query)

  def execute(implicit cypher: CypherDAO, executionContext: ExecutionContext): Future[JsValue] =
    cypher.cypher(query)
}

object CypherQuery {

  val ID = "userId"
  val NAME = "name"
  val QUERY = "query"
  val DESCRIPTION = "description"

  implicit val isoJodaDateReads = Reads.jodaDateReads("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  implicit val _format: Format[CypherQuery] = Json.format[CypherQuery]

  implicit val form = Form(
    mapping(
      "objectId" -> ignored(Option.empty[String]),
      ID -> optional(text),
      NAME -> nonEmptyText,
      QUERY -> nonEmptyText,
      DESCRIPTION -> optional(text),
      "createdAt" -> ignored(Option.empty[DateTime]),
      "updatedAt" -> ignored(Option.empty[DateTime])
    )(CypherQuery.apply)(CypherQuery.unapply)
  )
}
