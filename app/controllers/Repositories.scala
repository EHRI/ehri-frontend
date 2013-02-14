package controllers

import models.{Repository, RepositoryF, DocumentaryUnit,DocumentaryUnitF}
import models.forms.VisibilityForm
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._

object Repositories extends CRUD[RepositoryF,Repository]
  with CreationContext[DocumentaryUnitF,Repository]
	with VisibilityController[Repository]
  with PermissionScopeController[Repository]
  with EntityAnnotate[Repository] {

  val listFilterMappings = Map[String,String]()
  val orderMappings = Map[String,String]()
  val DEFAULT_SORT = "name"

  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = DocumentaryUnits.processChildParams(params)


  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Agent
  val contentType = ContentType.Agent

  val form = models.RepositoryForm.form
  val childForm = models.DocumentaryUnitForm.form
  val builder = Repository

  def get(id: String) = getWithChildrenAction(id, DocumentaryUnit.apply _) {
      item => page => params => annotations => implicit userOpt => implicit request =>
    Ok(views.html.repository.show(Repository(item), page, params, annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(Repository(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.repository.list(page.copy(list = page.list.map(Repository(_))), params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.repository.create(form,
        VisibilityForm.form, users, groups, routes.Repositories.createPost))
  }

  def createPost = createPostAction(models.RepositoryForm.form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups { users => groups =>
        BadRequest(views.html.repository.create(errorForm, accForm, users, groups, routes.Repositories.createPost))
      }
      case Right(item) => Redirect(routes.Repositories.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.repository.edit(Some(Repository(item)), form.fill(Repository(item).to), routes.Repositories.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.repository.edit(Some(Repository(item)), errorForm, routes.Repositories.updatePost(id)))
      case Right(item) => Redirect(routes.Repositories.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentType.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(Repository(item), childForm,
        VisibilityForm.form, users, groups, routes.Repositories.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentType.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(Repository(item),
          errorForm, accForm, users, groups, routes.Repositories.createDocPost(id)))
      }
      case Right(citem) => Redirect(routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(Repository(item), routes.Repositories.deletePost(id),
        routes.Repositories.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Repository(item),
      VisibilityForm.form.fill(Repository(item).accessors.map(_.id)),
      users, groups, routes.Repositories.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) = manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(Repository(item), perms, sperms,
        routes.Repositories.addItemPermissions(id), routes.Repositories.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Repository(item), users, groups,
        routes.Repositories.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Repository(item), users, groups,
        routes.Repositories.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(Repository(item), accessor, perms, contentType,
        routes.Repositories.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(Repository(item), accessor, perms, targetContentTypes,
        routes.Repositories.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(Repository(item), models.forms.AnnotationForm.form, routes.Repositories.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(Repository(item),
          errorForm, routes.Repositories.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Repositories.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}