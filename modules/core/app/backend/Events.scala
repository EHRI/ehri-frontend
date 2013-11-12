package backend

import scala.concurrent.Future
import utils.{SystemEventParams, ListParams, PageParams}
import models._
import models.base.AnyModel

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
trait Events {
  def subjectsForEvent(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[AnyModel]]
  def listEvents(params: ListParams, filters: SystemEventParams)(implicit apiUser: ApiUser): Future[List[SystemEvent]]
  def history(id: String, params: PageParams)(implicit apiUser: ApiUser): Future[Page[SystemEvent]]
}
