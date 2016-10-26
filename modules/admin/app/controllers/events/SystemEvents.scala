package controllers.events

import auth.AccountManager
import models.SystemEvent
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import javax.inject._

import auth.handler.AuthHandler
import utils.{MovedPageLookup, PageParams, RangeParams, SystemEventParams}
import controllers.generic.Read
import backend.DataApi
import backend.rest.DataHelpers
import models.base.AnyModel
import controllers.base.AdminController
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext

@Singleton
case class SystemEvents @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  dataApi: DataApi,
  dataHelpers: DataHelpers,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
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
