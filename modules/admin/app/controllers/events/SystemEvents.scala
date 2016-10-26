package controllers.events

import javax.inject._

import backend.rest.DataHelpers
import controllers.Components
import controllers.base.AdminController
import controllers.generic.Read
import models.SystemEvent
import models.base.AnyModel
import utils.{PageParams, RangeParams, SystemEventParams}


@Singleton
case class SystemEvents @Inject()(
  components: Components,
  dataHelpers: DataHelpers
) extends AdminController
  with Read[SystemEvent] {

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    val params = PageParams.fromRequest(request)
    val subjectParams = PageParams.fromRequest(request, namespace = "s")
    userDataApi.subjectsForEvent[AnyModel](id, subjectParams).map { page =>
      Ok(views.html.admin.systemEvent.show(request.item, page, params))
    }
  }

  def list = OptionalUserAction.async { implicit request =>
    val listParams = RangeParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    val filterForm = SystemEventParams.form.fill(eventFilter)

    for {
      users <- dataHelpers.getUserList
      events <- userDataApi.events[SystemEvent](listParams, eventFilter)
    } yield Ok(views.html.admin.systemEvent.list(events, listParams,
        filterForm, users,controllers.events.routes.SystemEvents.list()))
  }
}
