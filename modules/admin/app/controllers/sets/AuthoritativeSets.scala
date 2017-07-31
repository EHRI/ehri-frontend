package controllers.sets

import javax.inject._

import forms.VisibilityForm
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType}
import models._
import play.api.Configuration
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.{DataHelpers, IdGenerator}
import utils.{PageParams, RangeParams}
import services.search.{SearchConstants, SearchIndexMediator, SearchParams}

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class
AuthoritativeSets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  idGenerator: IdGenerator
) extends AdminController
  with CRUD[AuthoritativeSetF,AuthoritativeSet]
  with Creator[HistoricalAgentF, HistoricalAgent, AuthoritativeSet]
  with Visibility[AuthoritativeSet]
  with ScopePermissions[AuthoritativeSet]
  with Annotate[AuthoritativeSet]
  with Search {

  private val formDefaults: Option[Configuration] = config.getOptional[Configuration](EntityType.HistoricalAgent.toString)

  val targetContentTypes = Seq(ContentTypes.HistoricalAgent)
  private val form = models.AuthoritativeSet.form
  private val childForm = models.HistoricalAgent.form
  private val setRoutes = controllers.sets.routes.AuthoritativeSets


  def get(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[HistoricalAgent](params, paging = paging, filters = Map(SearchConstants.HOLDER_ID -> request.item.id)).map { result =>
      Ok(views.html.admin.authoritativeSet.show(
          request.item, result, setRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.list(request.page, request.params))
  }

  def create: Action[AnyContent] = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.create(form, VisibilityForm.form,
      request.users, request.groups, setRoutes.createPost()))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.authoritativeSet.create(errorForm, accForm,
          users, groups, setRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(setRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.edit(
      request.item, form.fill(request.item.model),setRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.authoritativeSet.edit(
        request.item, errorForm, setRoutes.updatePost(id)))
      case Right(item) => Redirect(setRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createHistoricalAgent(id: String): Action[AnyContent] = NewChildAction(id).async { implicit request =>
    idGenerator.getNextChildNumericIdentifier(id, EntityType.HistoricalAgent, "%06d").map { newid =>
      Ok(views.html.admin.historicalAgent.create(
        request.item, childForm.bind(Map(Entity.IDENTIFIER -> newid)),
        formDefaults, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups,
          setRoutes.createHistoricalAgentPost(id)))
    }
  }

  def createHistoricalAgentPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.historicalAgent.create(request.item,
          errorForm, formDefaults, accForm, users, groups, setRoutes.createHistoricalAgentPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, setRoutes.deletePost(id), setRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(setRoutes.list())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, setRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(setRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
          setRoutes.addItemPermissions(id), setRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        setRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        setRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        setRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        setRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(setRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def updateIndex(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
      Ok(views.html.admin.search.updateItemIndex(request.item, field = SearchConstants.HOLDER_ID,
          action = controllers.admin.routes.Indexing.indexer()))
  }

  def export(id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.AuthoritativeSet, id, Seq("eac"))
  }
}


