package controllers

import _root_.models.base.AccessibleEntity
import _root_.models.{Entity, DocumentaryUnit, ItemWithId}
import _root_.models.forms.{DatePeriodF, IsadG, DocumentaryUnitF, VisibilityForm}
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import base._
import defines.{PermissionType, ContentType, EntityType}
import rest.RestPageParams


object DocumentaryUnits extends CreationContext[DocumentaryUnitF, DocumentaryUnit]
  with VisibilityController[DocumentaryUnit]
  with EntityRead[DocumentaryUnit]
  with EntityUpdate[DocumentaryUnitF, DocumentaryUnit]
  with EntityDelete[DocumentaryUnit]
  with PermissionScopeController[DocumentaryUnit]
  with EntityAnnotate[DocumentaryUnit] {

  val DEFAULT_SORT = AccessibleEntity.NAME

  /**
   * Mapping between incoming list filter parameters
   * and the data values accessed via the server.
   */
  val listFilterMappings: Map[String,String] = Map(
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    AccessibleEntity.NAME -> AccessibleEntity.NAME,
    IsadG.ARCH_HIST -> s"<-describes.${IsadG.ARCH_HIST}",
    IsadG.SCOPE_CONTENT -> s"<-describes.${IsadG.SCOPE_CONTENT}",
    "date" -> s"->hasDate.${DatePeriodF.START_DATE}"
  )

  val orderMappings: Map[String,String] = Map(
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    AccessibleEntity.NAME -> AccessibleEntity.NAME,
    "date" -> s"->hasDate.${DatePeriodF.START_DATE}"
  )


  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }

  /**
   * Child list forms are handled the same as the main one
   * @param params
   * @return
   */
  override def processChildParams(params: ListParams) = processParams(params)

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val builder = DocumentaryUnit

  def get(id: String) = getWithChildrenAction(id, builder) { item => page => params => annotations =>
    implicit userOpt => implicit request =>
      Ok(views.html.documentaryUnit.show(DocumentaryUnit(item), page, params, annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(DocumentaryUnit(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.list(
      page.copy(list = page.list.map(DocumentaryUnit(_))), params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.edit(
        Some(DocumentaryUnit(item)), form.fill(DocumentaryUnit(item).to),routes.DocumentaryUnits.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.documentaryUnit.edit(
          Some(DocumentaryUnit(olditem)), errorForm, routes.DocumentaryUnits.updatePost(id)))
      case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(
      DocumentaryUnit(item), childForm, VisibilityForm.form, users, groups, routes.DocumentaryUnits.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, contentType) { item => formsOrItem =>
    implicit userOpt =>
      implicit request =>
        formsOrItem match {
          case Left((errorForm,accForm)) => getGroups(userOpt) { users => groups =>
            BadRequest(views.html.documentaryUnit.create(DocumentaryUnit(item),
              errorForm, accForm, users, groups, routes.DocumentaryUnits.createDocPost(id)))
          }
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt =>
    implicit request =>
      Ok(views.html.delete(
        DocumentaryUnit(item), routes.DocumentaryUnits.deletePost(id),
        routes.DocumentaryUnits.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.permissions.visibility(DocumentaryUnit(item),
        models.forms.VisibilityForm.form.fill(DocumentaryUnit(item).accessors.map(_.id)),
        users, groups, routes.DocumentaryUnits.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(routes.DocumentaryUnits.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit userOpt => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(DocumentaryUnit(item), perms, sperms,
        routes.DocumentaryUnits.addItemPermissions(id), routes.DocumentaryUnits.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit userOpt => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit userOpt => implicit request =>
      Ok(views.html.permissions.permissionItem(DocumentaryUnit(item), users, groups,
        routes.DocumentaryUnits.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit userOpt => implicit request =>
      Ok(views.html.permissions.setPermissionItem(DocumentaryUnit(item), accessor, perms, contentType,
        routes.DocumentaryUnits.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit userOpt => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit userOpt => implicit request =>
      Ok(views.html.permissions.setPermissionScope(DocumentaryUnit(item), accessor, perms, targetContentTypes,
        routes.DocumentaryUnits.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit userOpt => implicit request =>
      Redirect(routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit userOpt =>
    implicit request =>
      Ok(views.html.annotation.annotate(DocumentaryUnit(item),
        models.forms.AnnotationForm.form, routes.DocumentaryUnits.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit userOpt =>
    implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(DocumentaryUnit(item),
            errorForm, routes.DocumentaryUnits.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.DocumentaryUnits.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
    target => source => implicit userOpt => implicit request =>
      Ok(views.html.annotation.linkAnnotate(target, source,
        models.forms.AnnotationForm.form, routes.DocumentaryUnits.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) { formOrAnnotation => implicit userOpt =>
      implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.annotation.linkAnnotate(target, source,
          errorForm, routes.DocumentaryUnits.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(routes.DocumentaryUnits.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}


