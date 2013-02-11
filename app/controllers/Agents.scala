package controllers

import models.{Agent, DocumentaryUnit}
import models.forms.{DocumentaryUnitF, AgentF, VisibilityForm}
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._

object Agents extends CRUD[AgentF,Agent]
  with CreationContext[DocumentaryUnitF,Agent]
	with VisibilityController[Agent]
  with PermissionScopeController[Agent]
  with EntityAnnotate[Agent] {

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

  val form = models.forms.AgentForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val builder = Agent

  def get(id: String) = getWithChildrenAction(id, DocumentaryUnit.apply _) {
      item => page => params => annotations => implicit userOpt => implicit request =>
    Ok(views.html.agent.show(Agent(item), page, params, annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(Agent(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.agent.list(page.copy(list = page.list.map(Agent(_))), params))
  }

  def create = createAction {
      users => groups => implicit userOpt => implicit request =>
    Ok(views.html.agent.create(form,
        VisibilityForm.form, users, groups, routes.Agents.createPost))
  }

  def createPost = createPostAction(models.forms.AgentForm.form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups { users => groups =>
        BadRequest(views.html.agent.create(errorForm, accForm, users, groups, routes.Agents.createPost))
      }
      case Right(item) => Redirect(routes.Agents.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.agent.edit(Some(Agent(item)), form.fill(Agent(item).to), routes.Agents.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.agent.edit(Some(Agent(item)), errorForm, routes.Agents.updatePost(id)))
      case Right(item) => Redirect(routes.Agents.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentType.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(Agent(item), childForm,
        VisibilityForm.form, users, groups, routes.Agents.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentType.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(Agent(item),
          errorForm, accForm, users, groups, routes.Agents.createDocPost(id)))
      }
      case Right(citem) => Redirect(routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(Agent(item), routes.Agents.deletePost(id),
        routes.Agents.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Agents.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Agent(item),
      VisibilityForm.form.fill(Agent(item).accessors.map(_.id)),
      users, groups, routes.Agents.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.Agents.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) = manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(Agent(item), perms, sperms,
        routes.Agents.addItemPermissions(id), routes.Agents.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Agent(item), users, groups,
        routes.Agents.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Agent(item), users, groups,
        routes.Agents.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(Agent(item), accessor, perms, contentType,
        routes.Agents.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Agents.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(Agent(item), accessor, perms, targetContentTypes,
        routes.Agents.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.Agents.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(Agent(item), models.forms.AnnotationForm.form, routes.Agents.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(Agent(item),
          errorForm, routes.Agents.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Agents.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}