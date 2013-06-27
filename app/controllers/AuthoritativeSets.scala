package controllers

import models._
import models.forms.VisibilityForm
import play.api._
import play.api.i18n.Messages
import controllers.base._
import defines.{ContentType, EntityType}
import solr.{SearchOrder, SearchParams}
import scala.Some

object AuthoritativeSets extends CRUD[AuthoritativeSetF,AuthoritativeSetMeta]
  with CreationContext[HistoricalAgentF, HistoricalAgentMeta, AuthoritativeSetMeta]
  with VisibilityController[AuthoritativeSetMeta]
  with PermissionScopeController[AuthoritativeSetMeta]
  with EntityAnnotate[AuthoritativeSetMeta]
  with EntitySearch {

  val targetContentTypes = Seq(ContentType.HistoricalAgent)

  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(HistoricalAgents.listFilterMappings, HistoricalAgents.orderMappings, Some(HistoricalAgents.DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = HistoricalAgents.processParams(params)


  val entityType = EntityType.AuthoritativeSet
  val contentType = ContentType.AuthoritativeSet

  val form = models.forms.AuthoritativeSetForm.form
  val childForm = models.forms.HistoricalAgentForm.form

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(sort = Some(SearchOrder.Name), entities=List(entityType))


  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    searchAction[HistoricalAgentMeta](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.authoritativeSet.show(
          item, page, params, facets, routes.AuthoritativeSets.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.create(form, VisibilityForm.form, users, groups, routes.AuthoritativeSets.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.authoritativeSet.create(errorForm, accForm, users, groups, routes.AuthoritativeSets.createPost))
      }
      case Right(item) => Redirect(routes.AuthoritativeSets.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.edit(
      item, form.fill(item.model),routes.AuthoritativeSets.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.authoritativeSet.edit(
          olditem, errorForm, routes.AuthoritativeSets.updatePost(id)))
      case Right(item) => Redirect(routes.AuthoritativeSets.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createHistoricalAgent(id: String) = childCreateAction(id, ContentType.HistoricalAgent) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.create(
      item, childForm, VisibilityForm.form, users, groups,
        routes.AuthoritativeSets.createHistoricalAgentPost(id)))
  }

  def createHistoricalAgentPost(id: String) = childCreatePostAction(id, childForm, ContentType.HistoricalAgent) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.historicalAgent.create(item,
          errorForm, accForm, users, groups, routes.AuthoritativeSets.createHistoricalAgentPost(id)))
      }
      case Right(citem) => Redirect(routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, routes.AuthoritativeSets.deletePost(id),
        routes.AuthoritativeSets.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.AuthoritativeSets.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        models.forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, routes.AuthoritativeSets.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.AuthoritativeSets.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        routes.AuthoritativeSets.addItemPermissions(id), routes.AuthoritativeSets.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        routes.AuthoritativeSets.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        routes.AuthoritativeSets.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        routes.AuthoritativeSets.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.AuthoritativeSets.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        routes.AuthoritativeSets.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.AuthoritativeSets.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


