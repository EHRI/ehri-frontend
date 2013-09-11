package controllers.archdesc


import forms.VisibilityForm
import controllers.base._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import i18n.Messages
import defines._
import play.filters.csrf.CSRF.Token
import views.Helpers
import utils.search.{SearchParams, FacetSort}
import com.google.inject._
import solr.SolrConstants

@Singleton
class Repositories @Inject()(implicit val globalConfig: global.GlobalConfig) extends EntityRead[Repository]
  with EntityUpdate[RepositoryF, Repository]
  with EntityDelete[Repository]
  with CreationContext[DocumentaryUnitF,DocumentaryUnit, Repository]
	with VisibilityController[Repository]
  with PermissionScopeController[Repository]
  with EntityAnnotate[Repository]
  with EntitySearch
  with ApiBase[Repository] {

  val DEFAULT_SORT = "name"

  // Documentary unit facets
  import solr.facet._
  private val entityFacets = List(
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

  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  val entityType = EntityType.Repository
  val contentType = ContentTypes.Repository

  val form = models.forms.RepositoryForm.form
  val childForm = models.forms.DocumentaryUnitForm.form

  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))

  private val repositoryRoutes = controllers.archdesc.routes.Repositories


  def search = searchAction[Repository](defaultParams = Some(DEFAULT_SEARCH_PARAMS), entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.repository.search(page, params, facets, repositoryRoutes.search))
  }

  /**
   * Search documents inside repository.
   * @param id
   * @return
   */
  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>

    val filters = (if (request.getQueryString(SearchParams.QUERY).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++ Map(SolrConstants.HOLDER_ID -> item.id)

    searchAction[DocumentaryUnit](filters,
        defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
        entityFacets = entityFacets) {
      page => params => facets => _ => _ =>
        Ok(views.html.repository.show(item, page, params, facets, repositoryRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.repository.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.repository.edit(item, form.fill(item.model), repositoryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.repository.edit(item, errorForm, repositoryRoutes.updatePost(id)))
      case Right(item) => Redirect(repositoryRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentTypes.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(item, childForm,
        VisibilityForm.form, users, groups, repositoryRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentTypes.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    import play.filters.csrf._
    implicit val token: Option[Token] = CSRF.getToken(request)
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, accForm, users, groups, repositoryRoutes.createDocPost(id)))
      }
      case Right(citem) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, repositoryRoutes.deletePost(id),
        repositoryRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
      VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, repositoryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        repositoryRoutes.addItemPermissions(id), repositoryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        repositoryRoutes.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        repositoryRoutes.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        repositoryRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        repositoryRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}