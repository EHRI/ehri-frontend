package models

import play.api.libs.json.{Format, Json}
import play.api.mvc.RequestHeader


case class FeedbackContext(
  path: String,
  queryString: Map[String,Seq[String]],
  headers: Map[String,String]
)

object FeedbackContext {
  def fromRequest(implicit request: RequestHeader): FeedbackContext = FeedbackContext(
    path = request.path,
    queryString = request.queryString,
    headers = request.headers.toSimpleMap
  )

  implicit val _format: Format[FeedbackContext] = Json.format[FeedbackContext]
}
