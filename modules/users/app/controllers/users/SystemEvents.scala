package controllers.users

import defines._
import models.{AccountDAO, SystemEvent}
import play.api.libs.concurrent.Execution.Implicits._
import com.google.inject._
import global.GlobalConfig
import utils.{ListParams, SystemEventParams, PageParams}
import controllers.generic.Read
import backend.Backend
import backend.rest.RestHelpers

case class SystemEvents @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Read[SystemEvent] {

  implicit val resource = SystemEvent.Resource

  val entityType = EntityType.SystemEvent
  val contentType = ContentTypes.SystemEvent

  def get(id: String) = getAction.async(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    val params = PageParams.fromRequest(request)
    val subjectParams = PageParams.fromRequest(request, namespace = "s")
        backend.subjectsForEvent(id, params).map { page =>
      Ok(views.html.systemEvents.show(item, page, params))
    }
  }

  def list = userProfileAction.async { implicit userOpt => implicit request =>
    val listParams = ListParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    val filterForm = SystemEventParams.form.fill(eventFilter)

    for {
      users <- RestHelpers.getUserList
      events <- backend.listEvents(listParams, eventFilter)
    } yield Ok(views.html.systemEvents.list(events, listParams, filterForm, users))
  }
}
