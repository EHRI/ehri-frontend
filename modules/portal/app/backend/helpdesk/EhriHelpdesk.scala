package backend.helpdesk

import javax.inject.Inject

import backend.HelpdeskService
import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.WSClient


case class TestHelpdesk(implicit app: play.api.Application) extends HelpdeskService {
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


case class EhriHelpdesk @Inject() (implicit config: play.api.Configuration, ws: WSClient) extends HelpdeskService {
  def helpdeskUrl: String = config.getString("ehri.helpdesk.url")
    .getOrElse(sys.error("Configuration value: 'ehri.helpdesk.url' is not defined"))

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String,Double)]] = {
    // NB: Order is significant here, we want highest score first
    ws.url(helpdeskUrl).withQueryString(HelpdeskService.QUERY -> query).get().map { r =>
      r.json.as[Map[String,Double]].toSeq.sortWith { case (a, b) =>
        a._2 > b._2
      }
    }
  }

  def available(implicit executionContext: ExecutionContext): Future[Seq[(String,String)]] = {
    ws.url(helpdeskUrl).get().map { r =>
      r.json.as[Map[String,String]].toSeq
    }
  }
}
