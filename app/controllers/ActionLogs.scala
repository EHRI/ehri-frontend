package controllers

import defines._
import models.ActionLog
import controllers.base.EntityRead
import play.api.libs.concurrent.Execution.Implicits._


object ActionLogs extends EntityRead[ActionLog] {
  val entityType = EntityType.ActionLog
  val contentType = ContentType.ActionLog
  val showAction = routes.ActionLogs.get _
  val showView = views.html.actionLogs.show.apply _
  val listView = views.html.actionLogs.list.apply _

  val builder = ActionLog

  def get(id: String) = getAction(id) { item =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.actionLogs.show(ActionLog(item), maybeUser, request))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.actionLogs.list(page.copy(list = page.list.map(ActionLog(_))), maybeUser, request))
  }

  def historyFor(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = withUserAction { implicit user =>
    implicit request =>

    implicit val maybeUser = Some(user)
    AsyncRest {
      rest.ActionLogDAO(user)
        .history(id, math.max(page, 1), math.max(limit, 1)).map { itemOrErr =>
        itemOrErr.right.map {
          lst => Ok(views.html.actionLogs.itemList(id, lst.copy(list = lst.list.map(builder(_))), showAction, user, request))
        }
      }
    }
  }
}