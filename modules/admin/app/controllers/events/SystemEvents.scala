package controllers.events

import auth.AccountManager
import models.SystemEvent
import play.api.libs.concurrent.Execution.Implicits._
import com.google.inject._
import utils.{RangeParams, SystemEventParams, PageParams}
import controllers.generic.Read
import backend.Backend
import backend.rest.RestHelpers
import models.base.AnyModel
import controllers.base.AdminController

case class SystemEvents @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup)
  extends AdminController
  with Read[SystemEvent] {

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    val params = PageParams.fromRequest(request)
    val subjectParams = PageParams.fromRequest(request, namespace = "s")
    userBackend.subjectsForEvent[AnyModel](id, subjectParams).map { page =>
      Ok(views.html.admin.systemEvent.show(request.item, page, params))
    }
  }

  def list = OptionalUserAction.async { implicit request =>
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    val filterForm = SystemEventParams.form.fill(eventFilter)

    for {
      users <- RestHelpers.getUserList
      events <- userBackend.listEvents[SystemEvent](listParams, eventFilter)
    } yield Ok(views.html.admin.systemEvent.list(events, listParams,
        filterForm, users,controllers.events.routes.SystemEvents.list()))
  }
}
