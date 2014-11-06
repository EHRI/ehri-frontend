package controllers.events

import models.{AccountDAO, SystemEvent}
import play.api.libs.concurrent.Execution.Implicits._
import com.google.inject._
import utils.{RangeParams, SystemEventParams, PageParams}
import controllers.generic.Read
import backend.Backend
import backend.rest.RestHelpers
import models.base.AnyModel

case class SystemEvents @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Read[SystemEvent] {

  def get(id: String) = getAction.async(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    val params = PageParams.fromRequest(request)
    val subjectParams = PageParams.fromRequest(request, namespace = "s")
    backend.subjectsForEvent[AnyModel](id, subjectParams).map { page =>
      Ok(views.html.admin.systemEvents.show(item, page, params))
    }
  }

  def list = userProfileAction.async { implicit userOpt => implicit request =>
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    val filterForm = SystemEventParams.form.fill(eventFilter)

    for {
      users <- RestHelpers.getUserList
      events <- backend.listEvents[SystemEvent](listParams, eventFilter)
    } yield Ok(views.html.admin.systemEvents.list(events, listParams, filterForm, users))
  }
}
