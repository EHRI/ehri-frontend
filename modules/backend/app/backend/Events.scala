package backend

import scala.concurrent.Future
import utils._


/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Events {
  def subjectsForEvent[A: Readable](id: String, params: PageParams): Future[Page[A]]

  def listEvents[A: Readable](params: RangeParams, filters: SystemEventParams): Future[RangePage[A]]

  def listEventsByUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams): Future[RangePage[A]]

  def listEventsForUser[A: Readable](userId: String, params: RangeParams, filters: SystemEventParams): Future[RangePage[A]]

  def history[A: Readable](id: String, params: RangeParams, filters: SystemEventParams = SystemEventParams.empty): Future[RangePage[A]]

  def versions[V: Readable](id: String, params: PageParams): Future[Page[V]]
}
