package controllers.countries

import play.api.libs.concurrent.Execution.Implicits._
import _root_.forms.VisibilityForm
import controllers.generic._
import models._
import defines.{ContentTypes, EntityType}
import utils.search.{Resolver, Dispatcher}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{Entity, IdGenerator, Backend}
import play.api.Configuration
import play.api.Play.current
import solr.SolrConstants
import controllers.base.AdminController


@Singleton
case class Countries @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, idGenerator: IdGenerator, userDAO: AccountDAO)
  extends AdminController
  with CRUD[CountryF,Country]
  with Creator[RepositoryF, Repository, Country]
  with Visibility[Country]
  with ScopePermissions[Country]
  with Annotate[Country]
  with Search {

  /**
   * Content types that relate to this controller.
   */

  val targetContentTypes = Seq(ContentTypes.Repository, ContentTypes.DocumentaryUnit)

  private val childFormDefaults: Option[Configuration]
      = current.configuration.getConfig(EntityType.Repository)

  private val form = models.Country.form
  private val childForm = models.Repository.form

  private final val countryRoutes = controllers.countries.routes.Countries


  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    find[Repository](
      filters = Map(SolrConstants.COUNTRY_CODE -> request.item.id),
      entities = List(EntityType.Repository)
    ).map { result =>
      Ok(views.html.admin.country.show(request.item, result.page, result.params, result.facets,
        countryRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.systemEvents.itemList(item, page, params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.country.list(request.page, request.params))
  }

  def search = searchAction[Country](entities = List(EntityType.Country)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.admin.country.search(page, params, facets, countryRoutes.search()))
  }

  def create = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.country.create(form, VisibilityForm.form,
      request.users, request.groups, countryRoutes.createPost()))
  }

  def createPost = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.country.create(errorForm, accForm, users, groups, countryRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(countryRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.country.edit(
      request.item, form.fill(request.item.model),countryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.country.edit(
        request.item, errorForm, countryRoutes.updatePost(id)))
      case Right(item) => Redirect(countryRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createRepository(id: String) = NewChildAction(id).async { implicit request =>

    // Beware! This is dubious because there could easily be contention
    // if two repositories get created at the same time.
    // Currently there is not way to notify the user that they should just
    // reset the form or increment the ID manually.
    idGenerator.getNextNumericIdentifier(EntityType.Repository).map { newid =>
      val form = childForm.bind(Map(Entity.IDENTIFIER -> newid))
      Ok(views.html.admin.repository.create(
        request.item, form, childFormDefaults, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, countryRoutes.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.repository.create(request.item,
          errorForm, childFormDefaults, accForm, users, groups, countryRoutes.createRepositoryPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.institutions.routes.Repositories.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.delete(
        item, countryRoutes.deletePost(id),
        countryRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { implicit userOpt => implicit request =>
    Redirect(countryRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, countryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(countryRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(item, perms, sperms,
        countryRoutes.addItemPermissions(id), countryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionItem(item, users, groups,
        countryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionScope(item, users, groups,
        countryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionItem(item, accessor, perms, Country.Resource.contentType,
        countryRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        countryRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }
}

