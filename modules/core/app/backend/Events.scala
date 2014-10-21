package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{RangeParams, Page, SystemEventParams, PageParams}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Events {
  def subjectsForEvent[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def listEvents[A](params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def listEventsByUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def listEventsForUser[A](userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def history[A](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[A], executionContext: ExecutionContext): Future[Page[A]]

  def versions[V](id: String, params: PageParams)(implicit apiUser: ApiUser, rd: BackendReadable[V], executionContext: ExecutionContext): Future[Page[V]]
}
