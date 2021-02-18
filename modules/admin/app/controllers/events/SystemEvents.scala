package controllers.events

import javax.inject._

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic.Read
import models.SystemEvent
import models.base.Model
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import utils.{PageParams, RangeParams, SystemEventParams}


@Singleton
case class SystemEvents @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with Read[SystemEvent] {

  def get(id: String, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    userDataApi.subjectsForEvent[Model](id, paging).map { page =>
      Ok(views.html.admin.systemEvent.show(request.item, page, paging))
    }
  }

  def list(range: RangeParams): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    // Binding event params from the form instead of the query string binder
    // here because it allows doing multiselect values
    val form = SystemEventParams.form.bindFromRequest()
    val params = form.value.getOrElse(SystemEventParams.empty)
    for {
      users <- dataHelpers.getUserList
      events <- userDataApi.events[SystemEvent](range, params)
    } yield Ok(views.html.admin.systemEvent.list(events, range,
      form, users, controllers.events.routes.SystemEvents.list()))
  }
}
