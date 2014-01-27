package backend

import scala.concurrent.{ExecutionContext, Future}
import utils.{SystemEventParams, ListParams, PageParams}
import models._
import models.base.AnyModel

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Events {
  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[AnyModel]]
  def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[List[SystemEvent]]
  def listEventsForUser(userId: String, params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[List[SystemEvent]]
  def history(id: String, params: PageParams)(implicit apiUser: ApiUser, executionContext: ExecutionContext): Future[Page[SystemEvent]]
}
