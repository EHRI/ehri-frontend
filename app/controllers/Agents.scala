package controllers

import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._
import models.{DocumentaryUnit, Agent}
import models.forms.{AgentF,DocumentaryUnitF}
import rest.Page

object Agents extends CreationContext[DocumentaryUnitF,Agent]
	with VisibilityController[Agent]
	with CRUD[AgentF,Agent]
  with PermissionScopeController[Agent]
  with AnnotationController[Agent] {

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Agent
  val contentType = ContentType.Agent

  val form = models.forms.AgentForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val builder = Agent

  def get(id: String) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
      Ok(views.html.agent.show(Agent(item), annotations))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.agent.list(page.copy(list = page.list.map(Agent(_)))))
  }

  def create = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      Ok(views.html.agent.edit(None, form, routes.Agents.createPost))
  }

  def createPost = createPostAction(models.forms.AgentForm.form) { formOrItem =>
    implicit user =>
      implicit request =>
    formOrItem match {
      case Left(form) => BadRequest(views.html.agent.edit(None, form, routes.Agents.createPost))
      case Right(item) => Redirect(routes.Agents.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.agent.edit(Some(Agent(item)), form.fill(Agent(item).to), routes.Agents.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { item => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) =>
            BadRequest(views.html.agent.edit(Some(Agent(item)), errorForm, routes.Agents.updatePost(id)))
          case Right(item) => Redirect(routes.Agents.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
        }
  }

  def createDoc(id: String) = childCreateAction(id, ContentType.DocumentaryUnit) { item => implicit user =>
    implicit request =>
      Ok(views.html.documentaryUnit.create(
        Agent(item), childForm, routes.Agents.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentType.DocumentaryUnit) { item => formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(errorForm) =>
            BadRequest(views.html.documentaryUnit.create(Agent(item),
              errorForm, routes.Agents.createDocPost(id)))
          case Right(item) => Redirect(routes.DocumentaryUnits.get(item.id))
            .flashing("success" -> Messages("confirmations.itemWasCreate", item.id))
        }
  }

  def delete(id: String) = deleteAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.delete(
        Agent(item), routes.Agents.deletePost(id),
        routes.Agents.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Agents.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.visibility(Agent(item), users, groups, routes.Agents.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Agents.list())
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) = manageScopedPermissionsAction(id, page, spage, limit) {
    item => perms => sperms => implicit user => implicit request =>
      Ok(views.html.permissions.manageScopedPermissions(Agent(item), perms, sperms,
        routes.Agents.addItemPermissions(id), routes.Agents.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Agent(item), users, groups,
        routes.Agents.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
    item => users => groups => implicit user => implicit request =>
      Ok(views.html.permissions.permissionItem(Agent(item), users, groups,
        routes.Agents.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionItem(Agent(item), accessor, perms, contentType,
        routes.Agents.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
    bool => implicit user => implicit request =>
      Redirect(routes.Agents.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
    item => accessor => perms => implicit user => implicit request =>
      Ok(views.html.permissions.setPermissionScope(Agent(item), accessor, perms, targetContentTypes,
        routes.Agents.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
    perms => implicit user => implicit request =>
      Redirect(routes.Agents.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotate(Agent(item), models.forms.AnnotationForm.form, routes.Agents.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
          BadRequest(views.html.annotate(Agent(item),
            errorForm, routes.Agents.annotatePost(id)))
        }
        case Right(annotation) => {
          Redirect(routes.Agents.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }
}