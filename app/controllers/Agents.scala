package controllers

import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import defines._
import base._
import models.{DocumentaryUnit, Agent}
import models.forms.{AgentF,DocumentaryUnitF}
import java.lang.ProcessBuilder.Redirect


object Agents extends CreationContext[DocumentaryUnitF,Agent]
		with VisibilityController[AgentF,Agent]
		with CRUD[AgentF,Agent]
    with PermissionScopeController[Agent] {

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)
  val childContentType = ContentType.DocumentaryUnit
  val childEntityType = EntityType.DocumentaryUnit

  val managePermissionAction = routes.Agents.managePermissions _
  val manageScopedPermissionAction = routes.Agents.manageScopedPermissions _
  val managePermissionView = views.html.permissions.managePermissions.apply _
  val manageScopedPermissionView = views.html.permissions.manageScopedPermissions.apply _
  val addItemPermissionAction = routes.Agents.addItemPermissions _
  val addItemPermissionView = views.html.permissions.permissionItem.apply _
  val permissionItemAction = routes.Agents.permissionItem _
  val permissionItemView = views.html.permissions.setPermissionItem.apply _
  val setPermissionItemAction = routes.Agents.permissionItemPost _

  val addScopedPermissionAction = routes.Agents.addScopedPermissions _
  val addScopedPermissionView = views.html.permissions.permissionScope.apply _
  val permissionScopeAction = routes.Agents.permissionScope _
  val permissionScopeView = views.html.permissions.setPermissionScope.apply _
  val setPermissionScopeAction = routes.Agents.permissionScopePost _

  val entityType = EntityType.Agent
  val contentType = ContentType.Agent
  val createAction = routes.Agents.createPost
  val cancelAction = routes.Agents.get _
  val deleteAction = routes.Agents.deletePost _
  val childShowAction = routes.DocumentaryUnits.get _
  val childCreateAction = routes.Agents.childCreatePost _

  val setVisibilityAction = routes.Agents.visibilityPost _
  val visibilityAction = routes.Agents.visibility _
  val visibilityView = views.html.visibility.apply _    
  
  val form = models.forms.AgentForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val showAction = routes.Agents.get _
  val formView = views.html.agent.edit.apply _
  val showView = views.html.agent.show.apply _
  val showDocView = views.html.documentaryUnit.show.apply _
  val childFormView = views.html.documentaryUnit.create.apply _
  val listView = views.html.agent.list.apply _
  val deleteView = views.html.delete.apply _
  val builder = Agent

  def get(id: String) = getAction(id) { item =>
    implicit maybeUser =>
      implicit request =>
      Ok(views.html.agent.show(Agent(item), maybeUser, request))
  }

  def list(page: Int = 1, limit: Int = DEFAULT_LIMIT) = listAction(page, limit) { page =>
    implicit maybeUser =>
      implicit request =>
        Ok(views.html.agent.list(page.copy(list = page.list.map(Agent(_))), maybeUser, request))
  }

  def create = withContentPermission(PermissionType.Create, contentType) { implicit user =>
    implicit request =>
      Ok(views.html.agent.edit(None, form, routes.Agents.createPost, user, request))
  }

  def createPost = createPostAction(models.forms.AgentForm.form) { formOrItem =>
    implicit user =>
      implicit request =>
    formOrItem match {
      case Left(form) => BadRequest(views.html.agent.edit(None, form, routes.Agents.createPost, user, request))
      case Right(item) => Redirect(routes.Agents.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit user =>
    implicit request =>
      Ok(views.html.agent.edit(Some(Agent(item)), form.fill(Agent(item).to), routes.Agents.updatePost(id), user, request))
  }

  def updatePost(id: String) = updatePostAction(id, form) { formOrItem =>
    implicit user =>
      implicit request =>
        formOrItem match {
          case Left(form) => getEntity(id, Some(user)) { item =>
            BadRequest(views.html.agent.edit(Some(Agent(item)), form, routes.Agents.updatePost(id), user, request))
          }
          case Right(item) => Redirect(routes.Agents.get(item.id))
            .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
        }
  }

}