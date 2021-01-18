package controllers.sets

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType}
import forms._
import javax.inject._
import forms.FormConfigBuilder
import models.{Entity, _}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.{DataHelpers, IdGenerator}
import services.ingest.{IngestService, IngestParams}
import services.search.{SearchConstants, SearchIndexMediator, SearchParams}
import utils.{PageParams, RangeParams}


@Singleton
case class
AuthoritativeSets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  idGenerator: IdGenerator,
  ws: WSClient
) extends AdminController
  with CRUD[AuthoritativeSet]
  with Creator[HistoricalAgent, AuthoritativeSet]
  with Visibility[AuthoritativeSet]
  with ScopePermissions[AuthoritativeSet]
  with Annotate[AuthoritativeSet]
  with Promotion[AuthoritativeSet]
  with Search {

  private val formConfig: FormConfigBuilder = FormConfigBuilder(EntityType.HistoricalAgent, config)

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
    Ok(views.html.admin.authoritativeSet.create(form, visibilityForm,
      request.usersAndGroups, setRoutes.createPost()))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.authoritativeSet.create(errorForm, accForm,
          usersAndGroups, setRoutes.createPost()))
      case Right(item) => Redirect(setRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.authoritativeSet.edit(
      request.item, form.fill(request.item.data),setRoutes.updatePost(id)))
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
        formConfig.forCreate, visibilityForm.fill(request.item.accessors.map(_.id)),
        request.usersAndGroups,
          setRoutes.createHistoricalAgentPost(id)))
    }
  }

  def createHistoricalAgentPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.historicalAgent.create(request.item,
          errorForm, formConfig.forCreate, accForm, usersAndGroups, setRoutes.createHistoricalAgentPost(id)))
      case Right(citem) => Redirect(controllers.authorities.routes.HistoricalAgents.get(citem.id))
        .flashing("success" -> "item.create.confirmation")
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
        visibilityForm.fill(request.item.accessors.map(_.id)),
        request.usersAndGroups, setRoutes.visibilityPost(id)))
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
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
        setRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
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

  def promote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.promote(request.item, setRoutes.promotePost(id)))
  }

  def promotePost(id: String): Action[AnyContent] = PromoteItemAction(id).apply { implicit request =>
    Redirect(setRoutes.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.demote(request.item,
      setRoutes.demotePost(id)))
  }

  def demotePost(id: String): Action[AnyContent] = DemoteItemAction(id).apply { implicit request =>
    Redirect(setRoutes.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }

  def updateIndex(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
      Ok(views.html.admin.search.updateItemIndex(request.item, field = SearchConstants.HOLDER_ID,
          action = controllers.admin.routes.Indexing.indexer()))
  }

  def export(id: String): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    exportXml(EntityType.AuthoritativeSet, id, Seq("eac"))
  }

  def ingest(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    val dataType = IngestService.IngestDataType.Eac
    Ok(views.html.admin.tools.ingest(request.item, None, IngestParams.ingestForm, dataType,
      controllers.admin.routes.Ingest.ingestPost(ContentTypes.AuthoritativeSet, id, dataType)))
  }
}
