package controllers.countries

import javax.inject._

import services.rest.DataHelpers
import services.{Entity, IdGenerator}
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType}
import forms.VisibilityForm
import models._
import play.api.Configuration
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.{PageParams, RangeParams}
import utils.search.{SearchConstants, SearchIndexMediator, SearchParams}

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Countries @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  idGenerator: IdGenerator
) extends AdminController
  with CRUD[CountryF, Country]
  with Creator[RepositoryF, Repository, Country]
  with Visibility[Country]
  with ScopePermissions[Country]
  with Annotate[Country]
  with SearchType[Country]
  with Search {

  /**
    * Content types that relate to this controller.
    */
  override protected val targetContentTypes = Seq(ContentTypes.Repository, ContentTypes.DocumentaryUnit)

  private val childFormDefaults: Option[Configuration] = config.getOptional[Configuration](EntityType.Repository.toString)
  private val form = models.Country.form
  private val childForm = models.Repository.form

  private final val countryRoutes = controllers.countries.routes.Countries


  def get(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[Repository](params, paging, filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id)).map { result =>
      Ok(views.html.admin.country.show(request.item, result,
        countryRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging).apply { implicit request =>
      Ok(views.html.admin.country.search(request.result, countryRoutes.search()))
    }

  def create: Action[AnyContent] = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.country.create(form, VisibilityForm.form,
      request.users, request.groups, countryRoutes.createPost()))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.country.create(errorForm, accForm, users, groups, countryRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(countryRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.country.edit(
      request.item, form.fill(request.item.model), countryRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.country.edit(
        request.item, errorForm, countryRoutes.updatePost(id)))
      case Right(item) => Redirect(countryRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createRepository(id: String): Action[AnyContent] = NewChildAction(id).async { implicit request =>

    // Beware! This is dubious because there could easily be contention
    // if two repositories get created at the same time.
    // Currently there is not way to notify the user that they should just
    // reset the form or increment the ID manually.
    idGenerator.getNextNumericIdentifier(EntityType.Repository, "%06d").map { newid =>
      val form = childForm.bind(Map(Entity.IDENTIFIER -> newid))
      Ok(views.html.admin.repository.create(
        request.item, form, childFormDefaults, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, countryRoutes.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.repository.create(request.item,
          errorForm, childFormDefaults, accForm, users, groups, countryRoutes.createRepositoryPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.institutions.routes.Repositories.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, countryRoutes.deletePost(id),
      countryRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(countryRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, countryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(countryRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
        countryRoutes.addItemPermissions(id), countryRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
      countryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
      countryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        countryRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        countryRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(countryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }
}

