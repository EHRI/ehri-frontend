package services.data

import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.mvc.RequestHeader
import scala.collection

import java.time.ZonedDateTime

sealed trait DataServiceError extends RuntimeException

case class PermissionDenied(
  user: Option[String] = None,
  permission: Option[String] = None,
  item: Option[String] = None,
  scope: Option[String] = None
) extends RuntimeException(s"Permission denied on $item for $user") with DataServiceError

object PermissionDenied {
  private val _reads: Reads[PermissionDenied] = (
    (__ \ "details" \ "accessor").readNullable[String] and
    (__ \ "details" \ "permission").readNullable[String] and
    (__ \ "details" \ "item").readNullable[String] and
    (__ \ "details" \ "scope").readNullable[String]
  )(PermissionDenied.apply _)

  private val _writes: Writes[PermissionDenied] = (
    (__ \ "accessor").writeNullable[String] and
    (__ \ "permission").writeNullable[String] and
    (__ \ "item").writeNullable[String] and
    (__ \ "scope").writeNullable[String]
  )(unlift(PermissionDenied.unapply))

  implicit val _format: Format[PermissionDenied] = Format(_reads, _writes)
}

case class ValidationError(errorSet: ErrorSet) extends RuntimeException(errorSet.toString) with DataServiceError {
  override def toString: String = errorSet.toString
}

object ValidationError {
  def apply(field: String, error: String): ValidationError = ValidationError(ErrorSet(Map(field -> Seq(error))))

  implicit val _reads: Reads[ValidationError] = (
    (__ \ "error").read[String] and
    (__ \ "details").read[ErrorSet]
  )((_, s) => ValidationError(s))
}

case class JsonError(msg: String) extends RuntimeException(msg) with DataServiceError

case class HierarchyError(error: String, id: String, count: Int) extends RuntimeException(error) with DataServiceError
object HierarchyError {
  implicit val _reads: Reads[HierarchyError] = (
    (__ \ "details" \ "message").read[String] and
    (__ \ "details" \ "id").read[String] and
    (__ \ "details" \ "count").read[Int]
  )(HierarchyError.apply _)
}

case class InputDataError(error: String, details: String) extends RuntimeException(error) with DataServiceError
object InputDataError {
  implicit val _format: Format[InputDataError] = Json.format[InputDataError]
}

case class DeserializationError() extends RuntimeException() with DataServiceError

case class IntegrityError() extends RuntimeException() with DataServiceError

case class ItemNotFound(
  key: Option[String] = None,
  value: Option[String] = None,
  message: Option[String] = None,
  since: Option[ZonedDateTime] = None,
) extends RuntimeException(message.getOrElse("No further info")) with DataServiceError {
  def this(id: String) = this(Some("id"), Some(id))

  override def toString: String = s"Not found: $value, $message (since? $since)"
}

case class CriticalError(error: String) extends RuntimeException(error) with DataServiceError

case class BadJson(
  error: collection.Seq[(JsPath, collection.Seq[JsonValidationError])],
  url: Option[String] = None,
  data: Option[String] = None
) extends RuntimeException(error.toString) with DataServiceError {
  def prettyError: String = Json.prettyPrint(JsError.toJson(error))
  override def getMessage: String = s"""
        |Parsing error ${url.getOrElse("(no context)")}
        |
        |Data:
        |
        |${data.getOrElse("No data available")}
        |
        |Error:
        |
        |$prettyError
      """.stripMargin

  def getMessageWithContext(request: RequestHeader): String =
    s"Error at ${request.path}: $getMessage"
}

object ItemNotFound {
  private val _reads: Reads[ItemNotFound] = (
    (__ \ "details" \ "key").readNullable[String] and
    (__ \ "details" \ "value").readNullable[String] and
    (__ \ "details" \ "message").readNullable[String] and
    (__ \ "details" \ "since").readNullable[String].map(_.map(ZonedDateTime.parse))
  )(ItemNotFound.apply _)

  private val _writes: Writes[ItemNotFound] = (
    (__ \ "key").writeNullable[String] and
    (__ \ "value").writeNullable[String] and
    (__ \ "message").writeNullable[String] and
    (__ \ "since").writeNullable[ZonedDateTime]
  )(unlift(ItemNotFound.unapply))

  implicit val _format: Format[ItemNotFound] = Format(_reads, _writes)
}


