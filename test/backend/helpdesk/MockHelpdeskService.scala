package backend.helpdesk

import backend.HelpdeskService
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}

case class MockHelpdeskService(buffer: collection.mutable.HashMap[Int, Seq[(String, Double)]]) extends HelpdeskService {
  private def returnResponse(instId: String*): Seq[(String, Double)] = {
    val r = instId.toSeq.map(id => (id, 0.1))
    buffer += (buffer.size -> r)
    r
  }

  def available(implicit executionContext: ExecutionContext): Future[Seq[(String, String)]] = immediate {
    Seq("r1" -> "NIOD")
  }

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String, Double)]] = immediate {
    query match {
      case q if q.toLowerCase.contains("netherlands") => returnResponse("r1") // NIOD
      case _ => returnResponse("r2") // KCL
    }
  }
}