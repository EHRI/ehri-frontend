package controllers

import models.{DocumentaryUnit, ItemWithId}
import models.forms.{DocumentaryUnitF, VisibilityForm}
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import base._
import defines.{PermissionType, ContentType, EntityType}

object DocumentaryUnits extends CreationContext[DocumentaryUnitF, DocumentaryUnit]
  with VisibilityController[DocumentaryUnit]
  with EntityRead[DocumentaryUnit]
  with EntityUpdate[DocumentaryUnitF, DocumentaryUnit]
  with EntityDelete[DocumentaryUnit]
  with PermissionScopeController[DocumentaryUnit]
  with AnnotationController[DocumentaryUnit] {

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val builder = DocumentaryUnit

  def get(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = getWithChildrenAction(id, builder, page, limit) { item => page => annotations =>
    implicit maybeUser => implicit request =>
      Ok(views.html.documentaryUnit.show(DocumentaryUnit(item), page, annotations))
  }

  def history(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = historyAction(
    id, page, limit) { item => page => implicit maybeUser => implicit request =>
    Ok(views.html.actionLogs.itemList(DocumentaryUnit(item), page))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.documentaryUnit.list(page.copy(list = page.list.map(DocumentaryUnit(_)))))
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.documentaryUnit.edit(
        Some(DocumentaryUnit(item)), form.fill(DocumentaryUnit(item).to),routes.DocumentaryUnits.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) => BadRequest(views.html.documentaryUnit.edit(
              Some(DocumentaryUnit(olditem)), errorForm, routes.DocumentaryUnits.updatePost(id)))
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.documentaryUnit.create(
        DocumentaryUnit(item), childForm, VisibilityForm.form, users, groups, routes.DocumentaryUnits.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, contentType) { item => formsOrItem =>
    implicit user =>
      implicit request =>
        formsOrItem match {
          case Left((errorForm,accForm)) => getGroups(Some(user)) { users => groups =>
            BadRequest(views.html.documentaryUnit.create(DocumentaryUnit(item),
              errorForm, accForm, users, groups, routes.DocumentaryUnits.createDocPost(id)))
          }
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        DocumentaryUnit(item), routes.DocumentaryUnits.deletePost(id),
        routes.DocumentaryUnits.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.permissions.visibility(DocumentaryUnit(item),
        models.forms.VisibilityForm.form.fill(DocumentaryUnit(item).accessors.map(_.id)),
        users, groups, routes.DocumentaryUnits.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit user => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(DocumentaryUnit(item), perms, sperms,
        routes.DocumentaryUnits.addItemPermissions(id), routes.DocumentaryUnits.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(DocumentaryUnit(item), accessor, perms, contentType,
        routes.DocumentaryUnits.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionScope(DocumentaryUnit(item), accessor, perms, targetContentTypes,
        routes.DocumentaryUnits.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotation.annotate(DocumentaryUnit(item),
        models.forms.AnnotationForm.form, routes.DocumentaryUnits.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, Some(user)) { item =>
        BadRequest(views.html.annotation.annotate(DocumentaryUnit(item),
            errorForm, routes.DocumentaryUnits.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.DocumentaryUnits.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkAnnotate(id: String, src: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      getEntity(id, Some(user)) { srcitem =>
        Ok(views.html.annotation.linkAnnotate(DocumentaryUnit(item),
          ItemWithId(srcitem),
          models.forms.AnnotationForm.form, routes.DocumentaryUnits.linkAnnotatePost(id, src)))
      }
  }

  def linkAnnotatePost(id: String, src: String) = linkPostAction(id, src) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
          getEntity(src, Some(user)) { srcitem =>
            BadRequest(views.html.annotation.linkAnnotate(DocumentaryUnit(item), ItemWithId(srcitem),
              errorForm, routes.DocumentaryUnits.linkAnnotatePost(id, src)))
          }
        }
        case Right(annotation) => {
          Redirect(routes.DocumentaryUnits.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }

}


