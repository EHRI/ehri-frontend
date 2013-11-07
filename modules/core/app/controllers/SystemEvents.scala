package controllers.core

import defines._
import models.SystemEvent
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._
import com.google.inject._
import global.GlobalConfig
import utils.{ListParams, SystemEventParams, PageParams}
import rest.SystemEventDAO
import models.json.RestResource

class SystemEvents @Inject()(implicit val globalConfig: GlobalConfig) extends EntityRead[SystemEvent] {
  val entityType = EntityType.SystemEvent
  val contentType = ContentTypes.SystemEvent

  implicit val resource = SystemEvent.Resource

  def get(id: String) = getAction(id) { 
      item => annotations => links => implicit userOpt => implicit request =>
    // In addition to the item itself, we also want to fetch the subjects associated with it.
    AsyncRest {
      val params = PageParams.fromRequest(request)
      val subjectParams = PageParams.fromRequest(request, namespace = "s")
      rest.SystemEventDAO().subjectsFor(id, params).map { pageOrErr =>
        pageOrErr.right.map { page =>
          Ok(views.html.systemEvents.show(item, page, params))
        }
      }
    }
  }

  def list = userProfileAction { implicit userOpt => implicit request =>
    val listParams = ListParams.fromRequest(request)
    val eventFilter = SystemEventParams.fromRequest(request)
    val filterForm = SystemEventParams.form.fill(eventFilter)
    AsyncRest {
      SystemEventDAO().list(listParams, eventFilter).map { listOrErr =>
        listOrErr.right.map { list =>
          Ok(views.html.systemEvents.list(list, listParams, filterForm))
        }
      }
    }
  }
}