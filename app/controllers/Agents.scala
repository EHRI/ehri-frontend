package controllers

import models.base.AccessibleEntity
import defines._
import play.api.libs.concurrent.execution.defaultContext
import rest.EntityDAO
import controllers.base.CRUD
import models.Entity
import models.AgentRepr
import models.Agent


object Agents extends DocumentaryUnitContext[Agent,AgentRepr]
		with VisibilityController[Agent,AgentRepr]
		with CRUD[Agent,AgentRepr] {
  val entityType = EntityType.Agent
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
  
  val form = forms.AgentForm.form
  val docForm = forms.DocumentaryUnitForm.form
  val showAction = routes.Agents.get _
  val formView = views.html.agent.edit.apply _
  val showView = views.html.agent.show.apply _
  val showDocView = views.html.documentaryUnit.show.apply _
  val docFormView = views.html.documentaryUnit.create.apply _
  val listView = views.html.list.apply _
  val deleteView = views.html.delete.apply _
  val builder = AgentRepr
}