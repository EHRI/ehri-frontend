package controllers

import _root_.models.base.AccessibleEntity
import _root_.models._
import _root_.models.DocumentaryUnit
import _root_.models.forms.{AnnotationForm, VisibilityForm}
import _root_.models.Repository
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import i18n.Messages
import defines._
import _root_.controllers.base._
import play.filters.csrf.CSRF.Token
import collection.immutable.ListMap
import views.Helpers
import scala.Some
import solr.{SearchOrder, SearchParams}

object Repositories extends EntityRead[Repository]
  with EntityUpdate[RepositoryF, Repository]
  with EntityDelete[Repository]
  with CreationContext[DocumentaryUnitF,Repository]
	with VisibilityController[Repository]
  with PermissionScopeController[Repository]
  with EntityAnnotate[Repository]
  with EntitySearch {

  val listFilterMappings = ListMap[String,String](
    AccessibleEntity.NAME -> AccessibleEntity.NAME,
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    Isdiah.GEOCULTURAL_CONTEXT -> s"<-describes.${Isdiah.GEOCULTURAL_CONTEXT}"
  )

  val orderMappings = ListMap[String,String](
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    AccessibleEntity.NAME -> AccessibleEntity.NAME
  )
  val DEFAULT_SORT = "name"

  // Documentary unit facets
  import solr.facet._
  override val entityFacets = List(
    FieldFacetClass(
      key="countryCode",
      name=Messages("isdiah.countryCode"),
      param="country",
      render=Helpers.countryCodeToName,
      sort = FacetSort.Name
    ),
    FieldFacetClass(
      key="priority",
      name=Messages("priority"),
      param="priority",
      render=s => s match {
        case s if s == "0" => Messages("priority.zero")
        case s if s == "1" => Messages("priority.one")
        case s if s == "2" => Messages("priority.two")
        case s if s == "3" => Messages("priority.three")
        case s if s == "4" => Messages("priority.four")
        case s if s == "5" => Messages("priority.five")
        case s if s == "-1" => Messages("priority.reject")
        case _ => Messages("priority.unknown")
      }
    )
  )

  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = DocumentaryUnits.processChildParams(params)


  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Repository
  val contentType = ContentType.Repository

  val form = models.forms.RepositoryForm.form
  val childForm = models.forms.DocumentaryUnitForm.form

  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))


  def search = searchAction(defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
    page => params => facets => implicit userOpt => implicit request =>
      Ok(views.html.repository.search(page, params, facets, routes.Repositories.search))

  }

  /**
   * Search documents inside repository.
   * @param id
   * @return
   */
  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction(Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.repository.show(Repository(item), page, params, facets, routes.Repositories.get(id), annotations, links))
    }(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(Repository(item), page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.repository.list(page.copy(items = page.items.map(Repository(_))), params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
        println("Updating: " + Repository(item).formable)
    Ok(views.html.repository.edit(Repository(item), form.fill(Repository(item).formable), routes.Repositories.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.repository.edit(Repository(item), errorForm, routes.Repositories.updatePost(id)))
      case Right(item) => Redirect(routes.Repositories.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentType.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(Repository(item), childForm,
        VisibilityForm.form, users, groups, routes.Repositories.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentType.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    import play.filters.csrf._
    implicit val token: Option[Token] = CSRF.getToken(request)
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(Repository(item),
          errorForm, accForm, users, groups, routes.Repositories.createDocPost(id)))
      }
      case Right(citem) => Redirect(routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(Repository(item), routes.Repositories.deletePost(id),
        routes.Repositories.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Repository(item),
      VisibilityForm.form.fill(Repository(item).accessors.map(_.id)),
      users, groups, routes.Repositories.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) = manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(Repository(item), perms, sperms,
        routes.Repositories.addItemPermissions(id), routes.Repositories.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(Repository(item), users, groups,
        routes.Repositories.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(Repository(item), users, groups,
        routes.Repositories.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(Repository(item), accessor, perms, contentType,
        routes.Repositories.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(Repository(item), accessor, perms, targetContentTypes,
        routes.Repositories.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(Repository(item), AnnotationForm.form, routes.Repositories.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left(errorForm) => getEntity(id, userOpt) { item =>
        BadRequest(views.html.annotation.annotate(Repository(item),
          errorForm, routes.Repositories.annotatePost(id)))
      }
      case Right(annotation) => {
        Redirect(routes.Repositories.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}