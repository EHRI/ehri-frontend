package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{RangeParams, Page, SystemEventParams, PageParams}
import models._
import models.base.AnyModel

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Events {
  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[AnyModel]]

  def listEvents(params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]]

  def listEventsByUser(userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]]

  def listEventsForUser(userId: String, params: RangeParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]]

  def history(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]]

  def versions(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[Version]]
}
