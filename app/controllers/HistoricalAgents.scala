package controllers

import _root_.models.base.AccessibleEntity
import _root_.models._
import _root_.models.forms.{LinkForm, AnnotationForm, VisibilityForm}
import _root_.models.HistoricalAgent
import play.api._
import play.api.i18n.Messages
import defines._
import _root_.controllers.base._
import play.filters.csrf.CSRF.Token
import collection.immutable.ListMap
import views.Helpers
import solr.{SearchOrder, SearchParams}


object HistoricalAgents extends CRUD[HistoricalAgentF,HistoricalAgent]
	with VisibilityController[HistoricalAgent]
  with PermissionItemController[HistoricalAgent]
  with EntityLink[HistoricalAgent]
  with EntityAnnotate[HistoricalAgent]
  with EntitySearch {

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.HistoricalAgent
  val contentType = ContentType.HistoricalAgent

  val form = models.forms.HistoricalAgentForm.form

  val listFilterMappings = ListMap[String,String](
    AccessibleEntity.NAME -> s"<-describes.${Isaar.AUTHORIZED_FORM_OF_NAME}",
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    Isaar.HISTORY -> s"<-describes.${Isaar.HISTORY}"
  )

  val orderMappings = ListMap[String,String](
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    AccessibleEntity.NAME -> AccessibleEntity.NAME
  )

  val DEFAULT_SORT = s"<-describes.${Isaar.AUTHORIZED_FORM_OF_NAME}"

  // Documentary unit facets
  import solr.facet._
  override val entityFacets = List(
    FieldFacetClass(
      key=models.Isaar.ENTITY_TYPE,
      name=Messages(Isaar.FIELD_PREFIX + "." + Isaar.ENTITY_TYPE),
      param="cpf",
      render=s => Messages(Isaar.FIELD_PREFIX + "." + s)
    ),
    FieldFacetClass(
      key="holderName",
      name=Messages(s"$entityType.authoritativeSet"),
      param="set",
      sort = FacetSort.Name
    )
  )
  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = DocumentaryUnits.processChildParams(params)



  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))


  def search = {
    searchAction(defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.historicalAgent.search(page, params, facets, routes.HistoricalAgents.search))
    }
  }

  def get(id: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.show(HistoricalAgent(item), annotations, links))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(HistoricalAgent(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.list(page.copy(items = page.items.map(HistoricalAgent(_))), params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.edit(HistoricalAgent(item), form.fill(HistoricalAgent(item).formable), routes.HistoricalAgents.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.historicalAgent.edit(HistoricalAgent(item), errorForm, routes.HistoricalAgents.updatePost(id)))
      case Right(item) => Redirect(routes.HistoricalAgents.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(HistoricalAgent(item), routes.HistoricalAgents.deletePost(id),
        routes.HistoricalAgents.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.HistoricalAgents.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(HistoricalAgent(item),
      VisibilityForm.form.fill(HistoricalAgent(item).accessors.map(_.id)),
      users, groups, routes.HistoricalAgents.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.HistoricalAgents.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = manageItemPermissionsAction(id, page, limit) {
      item => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.managePermissions(HistoricalAgent(item), perms,
        routes.HistoricalAgents.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(HistoricalAgent(item), users, groups,
        routes.HistoricalAgents.setItemPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(HistoricalAgent(item), accessor, perms, contentType,
        routes.HistoricalAgents.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.HistoricalAgents.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = annotationAction(id) {
      item => form => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(HistoricalAgent(item), form, routes.HistoricalAgents.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(HistoricalAgent(item),
          errorForm, routes.HistoricalAgents.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.HistoricalAgents.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkTo(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.historicalAgent.linkTo(HistoricalAgent(item)))
  }

  def linkAnnotateSelect(id: String, toType: String) = linkSelectAction(id, toType) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.linking.linkSourceList(item, page, params,
        EntityType.withName(toType), routes.HistoricalAgents.linkAnnotate _))
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.linking.link(target, source,
        LinkForm.form, routes.HistoricalAgents.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) {
    formOrAnnotation => implicit userOpt => implicit request =>
      formOrAnnotation match {
        case Left((target,source,errorForm)) => {
          BadRequest(views.html.linking.link(target, source,
            errorForm, routes.HistoricalAgents.linkAnnotatePost(id, toType, to)))
        }
        case Right(annotation) => {
          Redirect(routes.HistoricalAgents.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }
}