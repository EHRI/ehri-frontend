package controllers

import models.base.AccessibleEntity
import defines._
import models.Entity
import models.ActionRepr
import controllers.base.EntityRead


object Actions extends EntityRead[ActionRepr] {
  val entityType = EntityType.Action
  val contentType = ContentType.Action
  val listAction = routes.Actions.list _
  val showAction = routes.Actions.get _
  val showView = views.html.action.show.apply _
  val listView = views.html.action.list.apply _

  val builder = ActionRepr
}