package controllers.events

import auth.AccountManager
import models.SystemEvent
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import javax.inject._
import utils.{MovedPageLookup, RangeParams, SystemEventParams, PageParams}
import controllers.generic.Read
import backend.Backend
import backend.rest.RestHelpers
import models.base.AnyModel
import controllers.base.AdminController
import views.MarkdownRenderer

case class SystemEvents @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends AdminController
  with Read[SystemEvent]
  with RestHelpers {

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
      users <- getUserList
      events <- userBackend.listEvents[SystemEvent](listParams, eventFilter)
    } yield Ok(views.html.admin.systemEvent.list(events, listParams,
        filterForm, users,controllers.events.routes.SystemEvents.list()))
  }
}
