package controllers

import play.api._
import play.api.mvc._
import base._
import defines.{ ContentType, EntityType }
import models.forms.DocumentaryUnitF
import models.DocumentaryUnit
import models.DocumentaryUnit

object DocumentaryUnits extends CreationContext[DocumentaryUnitF, DocumentaryUnit]
  with VisibilityController[DocumentaryUnitF, DocumentaryUnit]
  with EntityRead[DocumentaryUnit]
  with EntityUpdate[DocumentaryUnitF, DocumentaryUnit]
  with EntityDelete[DocumentaryUnit]
  with PermissionScopeController[DocumentaryUnit] {

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)
  val childContentType = ContentType.DocumentaryUnit
  val childEntityType = EntityType.DocumentaryUnit

  val managePermissionAction = routes.DocumentaryUnits.managePermissions _
  val manageScopedPermissionAction = routes.DocumentaryUnits.manageScopedPermissions _
  val managePermissionView = views.html.permissions.managePermissions.apply _
  val manageScopedPermissionView = views.html.permissions.manageScopedPermissions.apply _
  val addItemPermissionAction = routes.DocumentaryUnits.addItemPermissions _
  val addItemPermissionView = views.html.permissions.permissionItem.apply _
  val permissionItemAction = routes.DocumentaryUnits.permissionItem _
  val permissionItemView = views.html.permissions.setPermissionItem.apply _
  val setPermissionItemAction = routes.DocumentaryUnits.permissionItemPost _

  val addScopedPermissionAction = routes.DocumentaryUnits.addScopedPermissions _
  val addScopedPermissionView = views.html.permissions.permissionScope.apply _
  val permissionScopeAction = routes.DocumentaryUnits.permissionScope _
  val permissionScopeView = views.html.permissions.setPermissionScope.apply _
  val setPermissionScopeAction = routes.DocumentaryUnits.permissionScopePost _

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit
  val listAction = routes.DocumentaryUnits.list _
  val cancelAction = routes.DocumentaryUnits.get _
  val deleteAction = routes.DocumentaryUnits.deletePost _
  val updateAction = routes.DocumentaryUnits.updatePost _

  val setVisibilityAction = routes.DocumentaryUnits.visibilityPost _
  val visibilityAction = routes.DocumentaryUnits.visibility _
  val visibilityView = views.html.visibility.apply _

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val showAction = routes.DocumentaryUnits.get _
  val childShowAction = routes.DocumentaryUnits.get _
  val childCreateAction = routes.DocumentaryUnits.childCreatePost _
  val formView = views.html.documentaryUnit.edit.apply _
  val showView = views.html.documentaryUnit.show.apply _
  val listView = views.html.documentaryUnit.list.apply _
  val childFormView = views.html.documentaryUnit.create.apply _
  val deleteView = views.html.delete.apply _
  val builder = DocumentaryUnit
}


