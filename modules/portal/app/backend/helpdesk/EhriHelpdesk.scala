package backend.helpdesk

import backend.HelpdeskDAO
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WS


case class TestHelpdesk(implicit app: play.api.Application) extends HelpdeskDAO {
  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String, Double)]] = Future.successful {
    Seq (
      "us-005578" -> 0.0193,
      "it-002863" -> 0.0091
    )
  }

  def available(implicit executionContext: ExecutionContext): Future[Seq[(String, String)]] = Future.successful {
    Seq (
      "us-005578" -> "USHMM",
      "it-002863" -> "Ministero degli Affari Esteri"
    )
  }
}


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class EhriHelpdesk(implicit app: play.api.Application) extends HelpdeskDAO {
  def helpdeskUrl: String = app.configuration.getString("ehri.helpdesk.url")
    .getOrElse(sys.error("Configuration value: 'ehri.helpdesk.url' is not defined"))

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String,Double)]] = {
    // NB: Order is significant here, we want highest score first
    WS.url(helpdeskUrl).withQueryString(HelpdeskDAO.QUERY -> query).get().map { r =>
      r.json.as[Map[String,Double]].toSeq.sortWith { case (a, b) =>
        a._2 > b._2
      }
    }
  }

  def available(implicit executionContext: ExecutionContext): Future[Seq[(String,String)]] = {
    WS.url(helpdeskUrl).get().map { r =>
      r.json.as[Map[String,String]].toSeq
    }
  }
}
