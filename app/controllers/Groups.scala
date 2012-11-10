package controllers

import models.{AccessibleEntity,Group}
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.EntityDAO
import controllers.base.CRUD
import controllers.base.AccessorController

object Groups extends AccessorController[Group] with CRUD[Group] {
  val entityType = EntityType.Group
  val listAction = routes.Groups.list _
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val permsAction = routes.Groups.permissions _
  val setPermsAction = routes.Groups.permissionsPost _
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.permissions.edit.apply _
  val builder: (AccessibleEntity => Group) = Group.apply _
}

