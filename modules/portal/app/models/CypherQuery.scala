package models

import java.time.ZonedDateTime

import backend.rest.cypher.CypherService
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.{Constraint, Invalid, Valid}
import play.api.libs.json._
import play.api.libs.ws.StreamedResponse
import utils.CsvHelpers

import scala.concurrent.{ExecutionContext, Future}

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
  implicit val _reads = Json.reads[ResultFormat]

  def jsToString: PartialFunction[JsValue, String] = {
    case JsString(s) => s
    case JsNumber(i) => i.toString()
    case JsNull => ""
    case JsBoolean(b) => b.toString
    case list: JsArray => list.value.map(jsToString).mkString("|")
  }
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
  public: Boolean = false,
  createdAt: Option[ZonedDateTime] = None,
  updatedAt: Option[ZonedDateTime] = None
) {

  def download(implicit cypher: CypherService, executionContext: ExecutionContext): Future[StreamedResponse] =
    cypher.stream(query)

  def execute(implicit cypher: CypherService, executionContext: ExecutionContext): Future[JsValue] =
    cypher.cypher(query)
}

object CypherQuery {

  val ID = "userId"
  val NAME = "name"
  val QUERY = "query"
  val DESCRIPTION = "description"
  val PUBLIC = "public"

  implicit val _format: Format[CypherQuery] = Json.format[CypherQuery]

  // Mutation Cypher queries - if they add more and we don't update this
  // we're a bit screwed :|
  val dangerousClauses = "\\b(CREATE|SET|UPDATE|DELETE|MERGE|REMOVE|DETACH)\\b".r
  val isReadOnly = Constraint[String]("cypherQuery.mutatingClauses") { q =>
    dangerousClauses.findFirstIn(q.toUpperCase) match {
      case Some(clause) => Invalid("cypherQuery.mutatingClauses.error", clause)
      case None => Valid
    }
  }

  implicit val form = Form(
    mapping(
      "objectId" -> ignored(Option.empty[String]),
      ID -> optional(text),
      NAME -> nonEmptyText,
      QUERY -> nonEmptyText.verifying(isReadOnly),
      DESCRIPTION -> optional(text),
      PUBLIC -> boolean,
      "createdAt" -> ignored(Option.empty[ZonedDateTime]),
      "updatedAt" -> ignored(Option.empty[ZonedDateTime])
    )(CypherQuery.apply)(CypherQuery.unapply)
  )
}
