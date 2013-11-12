package controllers.authorities

import forms.VisibilityForm
import controllers.generic._
import models.{HistoricalAgent,HistoricalAgentF,AuthoritativeSet,AuthoritativeSetF}
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import utils.search.{Dispatcher, SearchOrder, SearchParams}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.Backend

@Singleton
case class AuthoritativeSets @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, backend: Backend) extends CRUD[AuthoritativeSetF,AuthoritativeSet]
  with Creator[HistoricalAgentF, HistoricalAgent, AuthoritativeSet]
  with Visibility[AuthoritativeSet]
  with ScopePermissions[AuthoritativeSet]
  with Annotate[AuthoritativeSet]
  with Search {

  implicit val resource = AuthoritativeSet.Resource
  val contentType = ContentTypes.AuthoritativeSet

  val targetContentTypes = Seq(ContentTypes.HistoricalAgent)
  private val form = models.forms.AuthoritativeSetForm.form
  private val childForm = models.forms.HistoricalAgentForm.form
  private val setRoutes = controllers.authorities.routes.AuthoritativeSets

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(sort = Some(SearchOrder.Name), entities=List(resource.entityType))


  def get(id: String) = getAction.async(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    searchAction[HistoricalAgent](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.HistoricalAgent)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.authoritativeSet.show(
          item, page, params, facets, setRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.create(form, VisibilityForm.form, users, groups, setRoutes.createPost))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.authoritativeSet.create(errorForm, accForm, users, groups, setRoutes.createPost))
      }
      case Right(item) => immediate(Redirect(setRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.authoritativeSet.edit(
      item, form.fill(item.model),setRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.authoritativeSet.edit(
          olditem, errorForm, setRoutes.updatePost(id)))
      case Right(item) => Redirect(setRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createHistoricalAgent(id: String) = childCreateAction(id, ContentTypes.HistoricalAgent) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.create(
      item, childForm, VisibilityForm.form, users, groups,
        setRoutes.createHistoricalAgentPost(id)))
  }

  def createHistoricalAgentPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.HistoricalAgent) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.historicalAgent.create(item,
          errorForm, accForm, users, groups, setRoutes.createHistoricalAgentPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, setRoutes.deletePost(id),
        setRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(setRoutes.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, setRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(setRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        setRoutes.addItemPermissions(id), setRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        setRoutes.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        setRoutes.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        setRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        setRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


