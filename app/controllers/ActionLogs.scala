package controllers

import defines._
import models.ActionLog
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._


object ActionLogs extends EntityRead[ActionLog] {
  val entityType = EntityType.ActionLog
  val contentType = ContentType.ActionLog

  val builder = ActionLog

  def get(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
      // In addition to the item itself, we also want to fetch the subjects associated with it.
      AsyncRest {
        rest.ActionLogDAO(maybeUser).subjectsFor(id, math.max(page, 1), math.max(limit, 1)).map { pageOrErr =>
          pageOrErr.right.map { page =>
            Ok(views.html.actionLogs.show(ActionLog(item), page))
          }
        }
      }
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.actionLogs.list(page.copy(list = page.list.map(ActionLog(_)))))
  }
}