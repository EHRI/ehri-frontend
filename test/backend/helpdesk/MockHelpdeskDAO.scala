package backend.helpdesk

import backend.HelpdeskDAO
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.Future.{successful => immediate}
import backend.HelpdeskDAO.HelpdeskResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockHelpdeskDAO() extends HelpdeskDAO {

  var buffer = Map.empty[Int, Seq[HelpdeskResponse]]

  private def returnResponse(instId: String*): Seq[HelpdeskResponse] = {
    val r = instId.toSeq.map(id => HelpdeskResponse(id, 0.1))
    buffer += (buffer.size -> r)
    r
  }

  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[HelpdeskResponse]] = immediate {
    query match {
      case q if q.toLowerCase.contains("netherlands") => returnResponse("r1") // NIOD
      case _ => returnResponse("r2") // KCL
    }
  }
}
