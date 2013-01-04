package controllers

import play.api._
import play.api.mvc._
import defines._
import base._
import models.Agent
import models.forms.AgentF


object Agents extends DocumentaryUnitCreator[AgentF,Agent]
		with VisibilityController[AgentF,Agent]
		with CRUD[AgentF,Agent]
    with PermissionScopeController[Agent] {

  val addScopedPermissionAction = routes.Agents.addScopedPermissions _
  val addScopedPermissionView = views.html.permissionScope.apply _
  val permissionScopeAction = routes.Agents.permissionScope _
  val permissionScopeView = views.html.setPermissionScope.apply _
  val setPermissionScopeAction = routes.Agents.permissionScopePost _

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Agent
  val contentType = ContentType.Agent
  val listAction = routes.Agents.list _
  val createAction = routes.Agents.createPost
  val updateAction = routes.Agents.updatePost _
  val cancelAction = routes.Agents.get _
  val deleteAction = routes.Agents.deletePost _
  val docShowAction = routes.DocumentaryUnits.get _
  val docCreateAction = routes.Agents.docCreatePost _

  val setVisibilityAction = routes.Agents.visibilityPost _
  val visibilityAction = routes.Agents.visibility _
  val visibilityView = views.html.visibility.apply _    
  
  val form = models.forms.AgentForm.form
  val docForm = models.forms.DocumentaryUnitForm.form
  val showAction = routes.Agents.get _
  val formView = views.html.agent.edit.apply _
  val showView = views.html.agent.show.apply _
  val showDocView = views.html.documentaryUnit.show.apply _
  val docFormView = views.html.documentaryUnit.create.apply _
  val listView = views.html.agent.list.apply _
  val deleteView = views.html.delete.apply _
  val builder = Agent
}