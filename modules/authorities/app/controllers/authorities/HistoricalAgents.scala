package controllers.authorities

import controllers.generic._
import forms.VisibilityForm
import models.{HistoricalAgent,HistoricalAgentF,Isaar}
import models.forms.LinkForm
import play.api.i18n.Messages
import defines.{ContentTypes,PermissionType}
import utils.search.{Dispatcher, SearchParams, FacetSort}
import com.google.inject._
import solr.SolrConstants
import backend.Backend

@Singleton
case class HistoricalAgents @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, backend: Backend) extends CRUD[HistoricalAgentF,HistoricalAgent]
	with Visibility[HistoricalAgent]
  with ItemPermissions[HistoricalAgent]
  with Linking[HistoricalAgent]
  with Annotate[HistoricalAgent]
  with Search {

  implicit val resource = HistoricalAgent.Resource

  val contentType = ContentTypes.HistoricalAgent

  private val form = models.forms.HistoricalAgentForm.form
  private val histRoutes = controllers.authorities.routes.HistoricalAgents

  // Documentary unit facets
  import solr.facet._
  private val entityFacets: FacetBuilder = { implicit lang =>
    List(
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages(Isaar.FIELD_PREFIX + "." + s)
      ),
      FieldFacetClass(
        key=SolrConstants.HOLDER_NAME,
        name=Messages("historicalAgent.authoritativeSet"),
        param="set",
        sort = FacetSort.Name
      )
    )
  }

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(resource.entityType))


  def search = searchAction[HistoricalAgent](defaultParams = Some(DEFAULT_SEARCH_PARAMS), entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.search(page, params, facets, histRoutes.search))
  }

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.show(item, annotations, links))
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.edit(item, form.fill(item.model), histRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.historicalAgent.edit(item, errorForm, histRoutes.updatePost(id)))
      case Right(item) => Redirect(histRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, histRoutes.deletePost(id),
        histRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(histRoutes.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
      VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, histRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(histRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageItemPermissionsAction(id) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.managePermissions(item, perms,
        histRoutes.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        histRoutes.setItemPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        histRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(histRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def linkTo(id: String) = withItemPermission[HistoricalAgent](id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: String) = linkSelectAction(id, toType) {
      item => page => params => facets => etype => implicit userOpt => implicit request =>
    Ok(views.html.link.linkSourceList(item, page, params, facets, etype,
        histRoutes.linkAnnotateSelect(id, toType),
        histRoutes.linkAnnotate _))
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.link.link(target, source,
        LinkForm.form, histRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) {
    formOrAnnotation => implicit userOpt => implicit request =>
      formOrAnnotation match {
        case Left((target,source,errorForm)) => {
          BadRequest(views.html.link.link(target, source,
            errorForm, histRoutes.linkAnnotatePost(id, toType, to)))
        }
        case Right(annotation) => {
          Redirect(histRoutes.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }
}