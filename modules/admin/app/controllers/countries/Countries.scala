package controllers.countries

import java.util.UUID

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.Materializer
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType, PermissionType}
import forms.VisibilityForm
import javax.inject._
import models._
import models.forms.FormConfigBuilder
import play.api.i18n.Messages
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.{ApiUser, DataHelpers, IdGenerator}
import services.geocoding.GeocodingService
import services.search.{SearchConstants, SearchIndexMediator, SearchParams}
import utils.{PageParams, RangeParams}

import scala.concurrent.Future


@Singleton
case class Countries @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  idGenerator: IdGenerator,
  geocoder: GeocodingService
)(implicit actorSystem: ActorSystem, mat: Materializer) extends AdminController
  with CRUD[Country]
  with Creator[Repository, Country]
  with Visibility[Country]
  with ScopePermissions[Country]
  with Annotate[Country]
  with SearchType[Country]
  with Search {

  /**
    * Content types that relate to this controller.
    */
  override protected val targetContentTypes = Seq(ContentTypes.Repository, ContentTypes.DocumentaryUnit)

  private val childFormConfig: FormConfigBuilder = FormConfigBuilder(EntityType.Repository, config)
  private val form = models.Country.form
  private val childForm = models.Repository.form

  private final val countryRoutes = controllers.countries.routes.Countries


  def get(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[Repository](params, paging, filters = Map(SearchConstants.COUNTRY_CODE -> request.item.id)).map { result =>
      Ok(views.html.admin.country.show(request.item, result,
        countryRoutes.get(id), request.annotations, request.links))
        .withPreferences(preferences.withRecentItem(id))
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
      request.usersAndGroups, countryRoutes.createPost()))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.country.create(
          errorForm, accForm, usersAndGroups, countryRoutes.createPost()))
      case Right(item) => Redirect(countryRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.country.edit(
      request.item, form.fill(request.item.data), countryRoutes.updatePost(id)))
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
        request.item, form, childFormConfig.forCreate, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.usersAndGroups, countryRoutes.createRepositoryPost(id)))
    }
  }

  def createRepositoryPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.repository.create(request.item,
          errorForm, childFormConfig.forCreate, accForm, usersAndGroups, countryRoutes.createRepositoryPost(id)))
      case Right(citem) => Redirect(controllers.institutions.routes.Repositories.get(citem.id))
        .flashing("success" -> "item.create.confirmation")
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
      request.usersAndGroups, countryRoutes.visibilityPost(id)))
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
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      countryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
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

  case class CountryGeocoder(id: String)(implicit apiUser: ApiUser, messages: Messages) extends Actor {

    private def logger = play.api.Logger(getClass)

    private def geocodeRepo(r: Repository, chan: ActorRef): Future[Option[RepositoryF]] = {
      r.data.descriptions.flatMap(_.addresses).headOption.map { a =>
        geocoder.geocode(a).map { point =>
            chan ! s"${r.id}: ${a.concise} = $point"
            Some(r.data.copy(latitude = point.map(_.latitude), longitude = point.map(_.longitude)))
        }
      }.getOrElse {
        chan ! s"${r.id}: no address found"
        Future.successful(Option.empty[RepositoryF])
      }
    }

    override def receive: Receive = {
      case chan: ActorRef =>
        val data = userDataApi
        .streamChildren[Country, Repository](id)
        .mapAsync(
          config.get[Option[Int]]("services.geocoding.parallelism").getOrElse(1))(
            r => geocodeRepo(r, chan))
        .mapError {
          case e => logger.error("Geocoding error: ", e); e
        }
        .collect { case Some(r) => r }
        .map(r => Json.obj(
          Entity.ID -> r.id,
          Entity.TYPE -> r.isA,
          Entity.DATA -> Json.obj(
            RepositoryF.LONGITUDE -> r.longitude,
            RepositoryF.LATITUDE -> r.latitude)
          )
        )

        userDataApi.batchUpdate(data, Some(id), version = true, commit = true,
              logMsg = "Update geographical info").map { done =>
            val msg = s"Finished Geocoding: $id: ${done.updated} updated, ${done.unchanged} unchanged"
            chan ! msg
            chan ! utils.WebsocketConstants.DONE_MESSAGE
            context.stop(self)
        }
    }
  }

  def geocode(id: String): Action[AnyContent] = AdminAction.async { implicit request =>
    userDataApi.get[Country](id).map { c =>
      val jobId = UUID.randomUUID().toString
      actorSystem.actorOf(Props(CountryGeocoder(id)), jobId)
      Redirect(countryRoutes.geocodeMonitor(id, jobId))
    }
  }

  def geocodeMonitor(id: String, jobId: String): Action[AnyContent] =
    WithParentPermissionAction(id, PermissionType.Update, ContentTypes.Repository).apply { implicit request =>
      Ok(views.html.admin.tasks.taskMonitor(
        Messages("geocode.monitor", request.item.toStringLang),
        controllers.admin.routes.Tasks.taskMonitorWS(jobId)))
    }
}

