package backend

import scala.concurrent.{ExecutionContext, Future}

case class BadHelpdeskResponse(msg: String, data: String) extends RuntimeException(msg)

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait HelpdeskDAO {
  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[HelpdeskDAO.HelpdeskResponse]]
}

object HelpdeskDAO {

  val QUERY = "query"

  case class HelpdeskResponse(institutionId: String, score: String, name: Option[String] = None)

  object HelpdeskResponse {
    import play.api.libs.json._
    import play.api.libs.functional.syntax._

    implicit val format: Format[HelpdeskResponse] = (
      (__ \ "id").format[String] and
      (__ \ "score").format[String] and
      (__ \ "name").formatNullable[String]
    )(HelpdeskResponse.apply, unlift(HelpdeskResponse.unapply))
  }
}