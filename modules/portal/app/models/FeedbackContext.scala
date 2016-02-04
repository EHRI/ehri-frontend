package models

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Headers, RequestHeader}
import org.joda.time.format.ISODateTimeFormat

case class FeedbackContext(
  path: String,
  queryString: Map[String,Seq[String]],
  headers: Map[String,String]
)

object FeedbackContext {

  private val dateTimeFormatter = ISODateTimeFormat.dateTime()

  def fromRequest(implicit request: RequestHeader): FeedbackContext = FeedbackContext(
    path = request.path,
    queryString = request.queryString,
    headers = request.headers.toSimpleMap
  )

  implicit val format: Format[FeedbackContext] = Json.format[FeedbackContext]
}
