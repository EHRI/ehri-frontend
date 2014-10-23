package backend

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait HelpdeskDAO {
  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[HelpdeskDAO.HelpdeskResponse]]
}

object HelpdeskDAO {

  val QUERY = "query"

  case class HelpdeskResponse(institutionId: String, rank: Double, email: Option[String] = None)

  object HelpdeskResponse {
    import play.api.libs.json._
    import play.api.libs.functional.syntax._

    implicit val format: Format[HelpdeskResponse] = (
      (__ \ "id").format[String] and
      (__ \ "rank").format[Double] and
      (__ \ "email").formatNullable[String]
    )(HelpdeskResponse.apply, unlift(HelpdeskResponse.unapply))
  }
}