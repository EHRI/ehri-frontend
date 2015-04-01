package backend

import scala.concurrent.{ExecutionContext, Future}
import utils._


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Events {
  def subjectsForEvent[A](id: String, params: PageParams)(implicit rd: Readable[A]): Future[Page[A]]

  def listEvents[A](params: RangeParams, filters: SystemEventParams)(implicit rd: Readable[A]): Future[RangePage[A]]

  def listEventsByUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit rd: Readable[A]): Future[RangePage[A]]

  def listEventsForUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit rd: Readable[A]): Future[RangePage[A]]

  def history[A](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty)(implicit rd: Readable[A]): Future[RangePage[A]]

  def versions[V](id: String, params: PageParams)(implicit rd: Readable[V]): Future[Page[V]]
}
