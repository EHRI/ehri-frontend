package backend

import scala.collection.immutable.TreeMap
import scala.concurrent.{ExecutionContext, Future}

case class BadHelpdeskResponse(msg: String, data: String) extends RuntimeException(msg)

trait HelpdeskService {
  def askQuery(query: String)(implicit executionContext: ExecutionContext): Future[Seq[(String,Double)]]
  def available(implicit executionContext: ExecutionContext): Future[Seq[(String,String)]]
}

object HelpdeskService {
  val QUERY = "q"
}