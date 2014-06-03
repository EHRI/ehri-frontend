package controllers.archdesc

import play.api.libs.concurrent.Execution.Implicits._
import _root_.forms.VisibilityForm
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import utils.search.{Resolver, SearchParams, Dispatcher}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{IdGenerator, Backend}
import play.api.Configuration
import play.api.Play.current


@Singleton
case class Countries @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, idGenerator: IdGenerator, userDAO: AccountDAO) extends CRUD[CountryF,Country]
  with Creator[RepositoryF, Repository, Country]
  with Visibility[Country]
  with ScopePermissions[Country]
  with Annotate[Country]
  with Search {

  /**
   * Content types that relate to this controller.
   */

  implicit val resource = Country.Resource

  val contentType = ContentTypes.Country
  val targetContentTypes = Seq(ContentTypes.Repository, ContentTypes.DocumentaryUnit)

  private val childFormDefaults: Option[Configuration]
      = current.configuration.getConfig(EntityType.Repository)

  private val form = models.Country.form
  private val childForm = models.Repository.form

  // Search memebers
  private val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(resource.entityType))

  private final val countryRoutes = controllers.archdesc.routes.Countries

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[Repository](Map("countryCode" -> item.id), defaultParams = Some(SearchParams(entities = List(EntityType.Repository)))) {
        page => params => facets => _ => _ =>
      Ok(views.html.country.show(item, page, params, facets, countryRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.country.list(page, params))
  }

  def search = searchAction[Country](defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.country.search(page, params, facets, countryRoutes.search))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.country.create(form, VisibilityForm.form, users, groups, countryRoutes.createPost()))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.country.create(errorForm, accForm, users, groups, countryRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(countryRoutes.get(item.id))
        .flashing("success" -> Messages("item.create.confirmation", item.id)))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.country.edit(item, form.fill(item.model),countryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.country.edit(
          olditem, errorForm, countryRoutes.updatePost(id)))
      case Right(item) => Redirect(countryRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("item.update.confirmation", item.id))
    }
  }

  def createRepository(id: String) = childCreateAction.async(id, ContentTypes.Repository) {
      item => users => groups => implicit userOpt => implicit request =>

    // Beware! This is dubious because there could easily be contention
    // if two repositories get created at the same time.
    // Currently there is not way to notify the user that they should just
    // reset the form or increment the ID manually.
    idGenerator.getNextNumericIdentifier(EntityType.Repository).map { newid =>
      val form = childForm.bind(Map(Entity.IDENTIFIER -> newid))
      Ok(views.html.repository.create(
        item, form, childFormDefaults, VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, countryRoutes.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.Repository) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.repository.create(item,
          errorForm, childFormDefaults, accForm, users, groups, countryRoutes.createRepositoryPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.archdesc.routes.Repositories.get(citem.id))
        .flashing("success" -> Messages("item.create.confirmation", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, countryRoutes.deletePost(id),
        countryRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(countryRoutes.search())
        .flashing("success" -> Messages("item.delete.confirmation", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, countryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(countryRoutes.get(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        countryRoutes.addItemPermissions(id), countryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        countryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        countryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        countryRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        countryRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }
}

