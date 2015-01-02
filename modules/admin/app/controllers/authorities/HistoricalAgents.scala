package controllers.authorities

import controllers.generic._
import forms.VisibilityForm
import models._
import play.api.i18n.Messages
import defines.{EntityType, PermissionType}
import utils.search.{FacetDisplay, Resolver, Dispatcher, FacetSort}
import com.google.inject._
import solr.SolrConstants
import backend.Backend
import controllers.base.AdminController


@Singleton
case class HistoricalAgents @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO)
  extends AdminController with CRUD[HistoricalAgentF,HistoricalAgent]
	with Visibility[HistoricalAgent]
  with ItemPermissions[HistoricalAgent]
  with Linking[HistoricalAgent]
  with Annotate[HistoricalAgent]
  with Search {

  private val form = models.HistoricalAgent.form
  private val histRoutes = controllers.authorities.routes.HistoricalAgents

  // Documentary unit facets
  import solr.facet._
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key=SolrConstants.HOLDER_NAME,
        name=Messages("historicalAgent.authoritativeSet"),
        param="set",
        sort = FacetSort.Name
      )
    )
  }


  def search = searchAction[HistoricalAgent](entities = List(EntityType.HistoricalAgent), entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.admin.historicalAgent.search(page, params, facets, histRoutes.search()))
  }

  def get(id: String) = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.show(request.item, request.annotations, request.links))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.historicalAgent.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.edit(
      request.item, form.fill(request.item.model), histRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.historicalAgent
          .edit(request.item, errorForm, histRoutes.updatePost(id)))
      case Right(updated) => Redirect(histRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(request.item, histRoutes.deletePost(id),
        histRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(histRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, histRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(histRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = PermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
        histRoutes.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        histRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions, HistoricalAgent.Resource.contentType,
        histRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(histRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = withItemPermission[HistoricalAgent](id, PermissionType.Annotate) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.admin.historicalAgent.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = linkSelectAction(id, toType, facets = entityFacets) {
      item => page => params => facets => etype => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.linkSourceList(item, page, params, facets, etype,
        histRoutes.linkAnnotateSelect(id, toType),
        histRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.create(target, source,
        Link.form, histRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = linkPostAction(id, toType, to) {
    formOrAnnotation => implicit userOpt => implicit request =>
      formOrAnnotation match {
        case Left((target,source,errorForm)) =>
          BadRequest(views.html.admin.link.create(target, source,
            errorForm, histRoutes.linkAnnotatePost(id, toType, to)))
        case Right(annotation) =>
          Redirect(histRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
  }
}