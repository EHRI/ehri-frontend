package controllers.authorities

import forms.VisibilityForm
import controllers.base._
import models._
import play.api._
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import utils.search.{Dispatcher, SearchOrder, SearchParams}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}

@Singleton
class AuthoritativeSets @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends CRUD[AuthoritativeSetF,AuthoritativeSet]
  with CreationContext[HistoricalAgentF, HistoricalAgent, AuthoritativeSet]
  with VisibilityController[AuthoritativeSet]
  with PermissionScopeController[AuthoritativeSet]
  with EntityAnnotate[AuthoritativeSet]
  with EntitySearch {

  val targetContentTypes = Seq(ContentTypes.HistoricalAgent)

  val entityType = EntityType.AuthoritativeSet
  val contentType = ContentTypes.AuthoritativeSet

  val form = models.forms.AuthoritativeSetForm.form
  val childForm = models.forms.HistoricalAgentForm.form

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(sort = Some(SearchOrder.Name), entities=List(entityType))


  def get(id: String) = getAction.async(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    searchAction[HistoricalAgent](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.authoritativeSet.show(
          item, page, params, facets, controllers.authorities.routes.AuthoritativeSets.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.create(form, VisibilityForm.form, users, groups, controllers.authorities.routes.AuthoritativeSets.createPost))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.authoritativeSet.create(errorForm, accForm, users, groups, controllers.authorities.routes.AuthoritativeSets.createPost))
      }
      case Right(item) => immediate(Redirect(controllers.authorities.routes.AuthoritativeSets.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.edit(
      item, form.fill(item.model),controllers.authorities.routes.AuthoritativeSets.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.authoritativeSet.edit(
          olditem, errorForm, controllers.authorities.routes.AuthoritativeSets.updatePost(id)))
      case Right(item) => Redirect(controllers.authorities.routes.AuthoritativeSets.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createHistoricalAgent(id: String) = childCreateAction(id, ContentTypes.HistoricalAgent) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.create(
      item, childForm, VisibilityForm.form, users, groups,
        controllers.authorities.routes.AuthoritativeSets.createHistoricalAgentPost(id)))
  }

  def createHistoricalAgentPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.HistoricalAgent) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.historicalAgent.create(item,
          errorForm, accForm, users, groups, controllers.authorities.routes.AuthoritativeSets.createHistoricalAgentPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.authorities.routes.AuthoritativeSets.deletePost(id),
        controllers.authorities.routes.AuthoritativeSets.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.authorities.routes.AuthoritativeSets.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.authorities.routes.AuthoritativeSets.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.authorities.routes.AuthoritativeSets.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.authorities.routes.AuthoritativeSets.addItemPermissions(id), controllers.authorities.routes.AuthoritativeSets.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.authorities.routes.AuthoritativeSets.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.authorities.routes.AuthoritativeSets.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.authorities.routes.AuthoritativeSets.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.authorities.routes.AuthoritativeSets.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.authorities.routes.AuthoritativeSets.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.authorities.routes.AuthoritativeSets.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


