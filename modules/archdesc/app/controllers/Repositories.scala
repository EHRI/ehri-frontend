package controllers.archdesc

import _root_.controllers.ListParams
import forms.VisibilityForm
import controllers.base._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import i18n.Messages
import defines._
import play.filters.csrf.CSRF.Token
import collection.immutable.ListMap
import views.Helpers
import utils.search.{SearchParams, FacetSort}
import utils.search.Dispatcher
import com.google.inject._

object Repositories {
  val listFilterMappings = ListMap[String,String](
    "name" -> "name",
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    Isdiah.GEOCULTURAL_CONTEXT -> s"<-describes.${Isdiah.GEOCULTURAL_CONTEXT}"
  )

  val orderMappings = ListMap[String,String](
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    "name" -> "name"
  )
}

@Singleton
class Repositories @Inject()(val searchDispatcher: Dispatcher) extends EntityRead[Repository]
  with EntityUpdate[RepositoryF, Repository]
  with EntityDelete[Repository]
  with CreationContext[DocumentaryUnitF,DocumentaryUnit, Repository]
	with VisibilityController[Repository]
  with PermissionScopeController[Repository]
  with EntityAnnotate[Repository]
  with EntitySearch
  with ApiBase[Repository] {

  /*private def getRepositoryTypes: Future[List[(String,String,String)]] = {

  }*/

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

  //override def processChildParams(params: ListParams) = DocumentaryUnits.processChildParams(params)


  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.Repository
  val contentType = ContentType.Repository

  val form = models.forms.RepositoryForm.form
  val childForm = models.forms.DocumentaryUnitForm.form

  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))


  def search = searchAction[Repository](defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.repository.search(page, params, facets, controllers.archdesc.routes.Repositories.search))

  }

  /**
   * Search documents inside repository.
   * @param id
   * @return
   */
  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[DocumentaryUnit](Map("holderId" -> item.id, "depthOfDescription" -> "0"),
        defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.repository.show(item, page, params, facets, controllers.archdesc.routes.Repositories.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    // TODO: Add relevant params
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.repository.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.repository.edit(item, form.fill(item.model), controllers.archdesc.routes.Repositories.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.repository.edit(item, errorForm, controllers.archdesc.routes.Repositories.updatePost(id)))
      case Right(item) => Redirect(controllers.archdesc.routes.Repositories.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, ContentType.DocumentaryUnit) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(item, childForm,
        VisibilityForm.form, users, groups, controllers.archdesc.routes.Repositories.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, ContentType.DocumentaryUnit) {
      item => formsOrItem => implicit userOpt => implicit request =>
    import play.filters.csrf._
    implicit val token: Option[Token] = CSRF.getToken(request)
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, accForm, users, groups, controllers.archdesc.routes.Repositories.createDocPost(id)))
      }
      case Right(citem) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(item, controllers.archdesc.routes.Repositories.deletePost(id),
        controllers.archdesc.routes.Repositories.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Repositories.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
      VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, controllers.archdesc.routes.Repositories.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Repositories.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) = manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.archdesc.routes.Repositories.addItemPermissions(id), controllers.archdesc.routes.Repositories.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.archdesc.routes.Repositories.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.archdesc.routes.Repositories.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.archdesc.routes.Repositories.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.archdesc.routes.Repositories.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.Repositories.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}