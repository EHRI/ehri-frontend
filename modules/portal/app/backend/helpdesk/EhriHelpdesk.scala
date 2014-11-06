package backend.helpdesk

import backend.HelpdeskDAO
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WS
import play.api.libs.json.Reads

case class TestHelpdesk(implicit app: play.api.Application) extends HelpdeskDAO {
  import HelpdeskDAO._

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[HelpdeskResponse]] = Future.successful {
    Seq (
      HelpdeskResponse("us-005578", 0.0193, Some("info@ushmm.org")),
      HelpdeskResponse("it-002863", 0.0091, Some("test@example.com"))
    )
  }
}


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class EhriHelpdesk(implicit app: play.api.Application) extends HelpdeskDAO {
  import HelpdeskDAO._

  def helpdeskUrl: String = app.configuration.getString("ehri.helpdesk.url")
    .getOrElse(sys.error("Configuration value: 'ehri.helpdesk.url' is not defined"))

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[HelpdeskResponse]] = {
    WS.url(helpdeskUrl).withQueryString("input" -> query).get().map { r =>
      (r.json \ "response").as[Seq[HelpdeskResponse]]
    }
  }
}
