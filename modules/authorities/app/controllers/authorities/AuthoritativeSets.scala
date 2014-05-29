package controllers.authorities

import play.api.libs.concurrent.Execution.Implicits._
import _root_.forms.VisibilityForm
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import utils.search.{Resolver, Dispatcher, SearchOrder, SearchParams}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{IdGenerator, Backend}

@Singleton
case class
AuthoritativeSets @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher,
            searchResolver: Resolver, backend: Backend, idGenerator: IdGenerator, userDAO: AccountDAO) extends CRUD[AuthoritativeSetF,AuthoritativeSet]
  with Creator[HistoricalAgentF, HistoricalAgent, AuthoritativeSet]
  with Visibility[AuthoritativeSet]
  with ScopePermissions[AuthoritativeSet]
  with Annotate[AuthoritativeSet]
  with Search {

  implicit val resource = AuthoritativeSet.Resource
  val contentType = ContentTypes.AuthoritativeSet

  val targetContentTypes = Seq(ContentTypes.HistoricalAgent)
  private val form = models.AuthoritativeSet.form
  private val childForm = models.HistoricalAgent.form
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
        .flashing("success" -> Messages("item.create.confirmation", item.id)))
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
        .flashing("success" -> play.api.i18n.Messages("item.update.confirmation", item.id))
    }
  }

  def createHistoricalAgent(id: String) = childCreateAction.async(id, ContentTypes.HistoricalAgent) {
      item => users => groups => implicit userOpt => implicit request =>
    idGenerator.getNextChildNumericIdentifier(id, EntityType.HistoricalAgent).map { newid =>
      Ok(views.html.historicalAgent.create(
        item, childForm.bind(Map(Entity.IDENTIFIER -> newid)),
          VisibilityForm.form, users, groups,
          setRoutes.createHistoricalAgentPost(id)))
    }
  }

  def createHistoricalAgentPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.HistoricalAgent) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.historicalAgent.create(item,
          errorForm, accForm, users, groups, setRoutes.createHistoricalAgentPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> Messages("item.create.confirmation", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, setRoutes.deletePost(id),
        setRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(setRoutes.list())
        .flashing("success" -> Messages("item.delete.confirmation", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, setRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(setRoutes.get(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
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

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        setRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        setRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }
}


