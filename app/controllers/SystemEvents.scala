package controllers

import defines._
import models.SystemEventMeta
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._


object SystemEvents extends EntityRead[SystemEventMeta] {
  val entityType = EntityType.SystemEvent
  val contentType = ContentType.SystemEvent

  def get(id: String) = getAction(id) { 
      item => annotations => links => implicit userOpt => implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    AsyncRest {
      val params = ListParams.bind(request)
      rest.SystemEventDAO(userOpt).subjectsFor(id, processParams(params)).map { pageOrErr =>
        pageOrErr.right.map { page =>
          // TODO: Create list params for subjects
          Ok(views.html.systemEvents.show(item, page, ListParams()))
        }
      }
    }
  }

  def list = listAction {
      page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.list(page, params))
  }
}