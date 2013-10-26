package controllers.core

import defines._
import models.SystemEvent
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._
import com.google.inject._
import global.GlobalConfig
import utils.PageParams

class SystemEvents @Inject()(implicit val globalConfig: GlobalConfig) extends EntityRead[SystemEvent] {
  val entityType = EntityType.SystemEvent
  val contentType = ContentTypes.SystemEvent

  def get(id: String) = getAction.async(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    val params = PageParams.fromRequest(request)
    val subjectParams = PageParams.fromRequest(request, namespace = "s")
    rest.SystemEventDAO(userOpt).subjectsFor(id, params).map { page =>
      Ok(views.html.systemEvents.show(item, page, params))
    }
  }

  def list = pageAction {
      page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.list(page, params))
  }
}
