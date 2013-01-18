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

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val builder = DocumentaryUnit

  def get(id: String) = getAction(id) { item =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.documentaryUnit.show(DocumentaryUnit(item), maybeUser, request))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.documentaryUnit.list(page.copy(list = page.list.map(DocumentaryUnit(_))), maybeUser, request))
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.documentaryUnit.edit(
        Some(DocumentaryUnit(item)), form.fill(DocumentaryUnit(item).to),routes.DocumentaryUnits.updatePost(id), user, request))
  }

  def updatePost(id: String) = updatePostAction(id, form) { formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(form) => getEntity(id, Some(user)) { item =>
            BadRequest(views.html.documentaryUnit.edit(
              Some(DocumentaryUnit(item)), form, routes.DocumentaryUnits.updatePost(id), user, request))
          }
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def createDoc(id: String) = childCreateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.documentaryUnit.create(
        DocumentaryUnit(item), childForm, routes.DocumentaryUnits.createDocPost(id), user, request))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm) { formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(form) => getEntity(id, Some(user)) { item =>
            BadRequest(views.html.documentaryUnit.create(DocumentaryUnit(item),
              form, routes.DocumentaryUnits.createDocPost(id), user, request))
          }
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasCreate", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        DocumentaryUnit(item), routes.DocumentaryUnits.deletePost(id),
        routes.DocumentaryUnits.get(id), user, request))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.list())
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.visibility(DocumentaryUnit(item), users, groups, routes.DocumentaryUnits.visibilityPost(id), user, request))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.list())
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit user => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(DocumentaryUnit(item), perms, sperms,
        routes.DocumentaryUnits.addItemPermissions(id), routes.DocumentaryUnits.get(id), user, request))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setItemPermissions _, user, request))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setScopedPermissions _, user, request))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(DocumentaryUnit(item), accessor, perms, contentType,
        routes.DocumentaryUnits.setItemPermissionsPost(id, userType, userId), user, request))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionScope(DocumentaryUnit(item), accessor, perms, targetContentTypes,
        routes.DocumentaryUnits.setScopedPermissionsPost(id, userType, userId), user, request))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", id))
  }
}


