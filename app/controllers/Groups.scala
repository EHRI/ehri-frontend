package controllers

import controllers.base.CRUD
import controllers.base.PermissionsController
import controllers.base.VisibilityController
import defines.EntityType
import models.Group
import models.GroupRepr

object Groups extends PermissionsController[Group, GroupRepr] 
		with VisibilityController[Group,GroupRepr]
		with CRUD[Group, GroupRepr] {
  val entityType = EntityType.Group
  val listAction = routes.Groups.list _
  val createAction = routes.Groups.createPost
  val updateAction = routes.Groups.updatePost _
  val cancelAction = routes.Groups.get _
  val deleteAction = routes.Groups.deletePost _
  val permsAction = routes.Groups.permissions _
  val setPermsAction = routes.Groups.permissionsPost _
  
  val setVisibilityAction = routes.Groups.visibilityPost _
  val visibilityAction = routes.Groups.visibility _
  val visibilityView = views.html.visibility.apply _  
  
  val form = forms.GroupForm.form
  val showAction = routes.Groups.get _
  val formView = views.html.group.edit.apply _
  val showView = views.html.group.show.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val permView = views.html.permissions.edit.apply _
  val builder = GroupRepr
}
