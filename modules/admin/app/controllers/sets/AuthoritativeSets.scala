package controllers.sets

import auth.AccountManager
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import _root_.forms.VisibilityForm
import controllers.generic._
import models._
import defines.{ContentTypes, EntityType}
import utils.MovedPageLookup
import utils.search.{SearchConstants, SearchEngine, SearchIndexMediator, SearchItemResolver}
import javax.inject._

import auth.handler.AuthHandler
import backend.rest.DataHelpers
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}
import backend.{DataApi, Entity, IdGenerator}
import play.api.Configuration
import controllers.base.AdminController

import scala.concurrent.ExecutionContext


@Singleton
case class
AuthoritativeSets @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  searchEngine: SearchEngine,
  searchIndexer: SearchIndexMediator,
  searchResolver: SearchItemResolver,
  dataApi: DataApi,
  dataHelpers: DataHelpers,
  idGenerator: IdGenerator,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends AdminController
  with CRUD[AuthoritativeSetF,AuthoritativeSet]
  with Creator[HistoricalAgentF, HistoricalAgent, AuthoritativeSet]
  with Visibility[AuthoritativeSet]
  with ScopePermissions[AuthoritativeSet]
  with Annotate[AuthoritativeSet]
  with Indexable[AuthoritativeSet]
  with Search {

  private val formDefaults: Option[Configuration] = config.getConfig(EntityType.HistoricalAgent.toString)

  val targetContentTypes = Seq(ContentTypes.HistoricalAgent)
  private val form = models.AuthoritativeSet.form
  private val childForm = models.HistoricalAgent.form
  private val setRoutes = controllers.sets.routes.AuthoritativeSets


  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    findType[HistoricalAgent](
      filters = Map(SearchConstants.HOLDER_ID -> request.item.id)
    ).map { result =>
      Ok(views.html.admin.authoritativeSet.show(
          request.item, result, setRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.list(request.page, request.params))
  }

  def create = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.create(form, VisibilityForm.form,
      request.users, request.groups, setRoutes.createPost()))
  }

  def createPost = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.authoritativeSet.create(errorForm, accForm,
          users, groups, setRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(setRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.edit(
      request.item, form.fill(request.item.model),setRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.authoritativeSet.edit(
        request.item, errorForm, setRoutes.updatePost(id)))
      case Right(item) => Redirect(setRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createHistoricalAgent(id: String) = NewChildAction(id).async { implicit request =>
    idGenerator.getNextChildNumericIdentifier(id, EntityType.HistoricalAgent, "%06d").map { newid =>
      Ok(views.html.admin.historicalAgent.create(
        request.item, childForm.bind(Map(Entity.IDENTIFIER -> newid)),
        formDefaults, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups,
          setRoutes.createHistoricalAgentPost(id)))
    }
  }

  def createHistoricalAgentPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.historicalAgent.create(request.item,
          errorForm, formDefaults, accForm, users, groups, setRoutes.createHistoricalAgentPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, setRoutes.deletePost(id), setRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(setRoutes.list())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, setRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(setRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        setRoutes.addItemPermissions(id), setRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        setRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        setRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        setRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        setRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def updateIndex(id: String) = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
      Ok(views.html.admin.search.updateItemIndex(request.item,
          action = setRoutes.updateIndexPost(id)))
  }

  def updateIndexPost(id: String) = updateChildItemsPost(SearchConstants.HOLDER_ID, id)

  def export(id: String) = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.AuthoritativeSet, id, Seq("eac"))
  }
}


