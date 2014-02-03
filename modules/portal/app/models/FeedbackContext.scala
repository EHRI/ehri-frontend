package models

import org.joda.time.DateTime
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Headers, RequestHeader}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class FeedbackContext(
  path: String,
  queryString: Map[String,Seq[String]],
  headers: Map[String,String],
  dateTime: DateTime = DateTime.now()
)

object FeedbackContext {

  def fromRequest(implicit request: RequestHeader): FeedbackContext = FeedbackContext(
    path = request.path,
    queryString = request.queryString,
    headers = request.headers.toSimpleMap,
    dateTime = DateTime.now()
  )

  implicit val format: Format[FeedbackContext] = Json.format[FeedbackContext]
}
