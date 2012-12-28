package controllers

import defines._
import models.ActionLog
import controllers.base.EntityRead


object ActionLogs extends EntityRead[ActionLog] {
  val entityType = EntityType.ActionLog
  val contentType = ContentType.ActionLog
  val listAction = routes.ActionLogs.list _
  val showAction = routes.ActionLogs.get _
  val showView = views.html.actionLogs.show.apply _
  val listView = views.html.actionLogs.list.apply _

  val builder = ActionLog
}