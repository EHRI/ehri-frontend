package backend

import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

case class BadHelpdeskResponse(msg: String, data: String) extends RuntimeException(msg)

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait HelpdeskDAO {
  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String,Double)]]
  def available(implicit executionContext: ExecutionContext): Future[Seq[(String,String)]]
}

object HelpdeskDAO {
  val QUERY = "q"
}