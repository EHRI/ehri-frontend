package controllers.datasets

import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import controllers.generic._
import models._
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import play.api.cache.AsyncCacheApi
import play.api.http.MimeTypes
import play.api.libs.json.{Format, Json}
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import services.data.ItemNotFound
import services.datasets.{ImportDatasetExists, ImportDatasetService}
import services.ingest.ImportLogService
import services.storage.FileStorage

import javax.inject._
import scala.concurrent.Future
import scala.util.control.NonFatal


case class RepositoryDatasets(repoId: String, name: String, altNames: String, logoUrl: String, sets: Seq[ImportDataset])
object RepositoryDatasets {
  implicit val _format: Format[RepositoryDatasets] = Json.format[RepositoryDatasets]
}

@Singleton
case class ImportDatasets @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  @Named("dam") storage: FileStorage,
  datasets: ImportDatasetService,
  asyncCache: AsyncCacheApi,
  importLogService: ImportLogService,
)(implicit mat: Materializer) extends AdminController with ApiBodyParsers with StorageHelpers with Update[Repository] {

  def jsRoutes(): Action[AnyContent] = Action.apply { implicit request =>
    Ok(
      JavaScriptReverseRouter("datasetApi")(
        controllers.admin.routes.javascript.Data.i18n,
        controllers.admin.routes.javascript.Tasks.taskMonitorWS,
        controllers.admin.routes.javascript.Tasks.cancel,
        controllers.datasets.routes.javascript.ImportDatasets.manager,
        controllers.datasets.routes.javascript.ImportDatasets.list,
        controllers.datasets.routes.javascript.ImportDatasets.listAll,
        controllers.datasets.routes.javascript.ImportDatasets.stats,
        controllers.datasets.routes.javascript.ImportDatasets.create,
        controllers.datasets.routes.javascript.ImportDatasets.update,
        controllers.datasets.routes.javascript.ImportDatasets.delete,
        controllers.datasets.routes.javascript.ImportDatasets.batch,
        controllers.datasets.routes.javascript.ImportDatasets.fileCount,
        controllers.datasets.routes.javascript.ImportDatasets.errors,
        controllers.datasets.routes.javascript.ImportFiles.listFiles,
        controllers.datasets.routes.javascript.ImportFiles.info,
        controllers.datasets.routes.javascript.ImportFiles.validateFiles,
        controllers.datasets.routes.javascript.ImportFiles.deleteFiles,
        controllers.datasets.routes.javascript.ImportFiles.uploadHandle,
        controllers.datasets.routes.javascript.ImportFiles.fileUrls,
        controllers.datasets.routes.javascript.ImportFiles.copyFile,
        controllers.datasets.routes.javascript.PrettyPrinting.reformat,
        controllers.datasets.routes.javascript.HarvestConfigs.harvest,
        controllers.datasets.routes.javascript.HarvestConfigs.get,
        controllers.datasets.routes.javascript.HarvestConfigs.save,
        controllers.datasets.routes.javascript.HarvestConfigs.delete,
        controllers.datasets.routes.javascript.HarvestConfigs.test,
        controllers.datasets.routes.javascript.HarvestConfigs.clean,
        controllers.datasets.routes.javascript.ConvertConfigs.convertFile,
        controllers.datasets.routes.javascript.ConvertConfigs.convert,
        controllers.datasets.routes.javascript.ConvertConfigs.get,
        controllers.datasets.routes.javascript.ConvertConfigs.save,
        controllers.datasets.routes.javascript.DataTransformations.list,
        controllers.datasets.routes.javascript.DataTransformations.get,
        controllers.datasets.routes.javascript.DataTransformations.create,
        controllers.datasets.routes.javascript.DataTransformations.update,
        controllers.datasets.routes.javascript.DataTransformations.delete,
        controllers.datasets.routes.javascript.ImportConfigs.ingestFiles,
        controllers.datasets.routes.javascript.ImportConfigs.get,
        controllers.datasets.routes.javascript.ImportConfigs.save,
        controllers.datasets.routes.javascript.ImportConfigs.delete,
        controllers.datasets.routes.javascript.ImportLogs.listSnapshots,
        controllers.datasets.routes.javascript.ImportLogs.takeSnapshot,
        controllers.datasets.routes.javascript.ImportLogs.diffSnapshot,
        controllers.datasets.routes.javascript.ImportLogs.cleanup,
        controllers.datasets.routes.javascript.ImportLogs.listCleanups,
        controllers.datasets.routes.javascript.ImportLogs.getCleanup,
        controllers.datasets.routes.javascript.ImportLogs.doCleanup,
        controllers.datasets.routes.javascript.ImportLogs.doCleanupAsync,
        controllers.datasets.routes.javascript.ImportLogs.list,
        controllers.datasets.routes.javascript.CoreferenceTables.getTable,
        controllers.datasets.routes.javascript.CoreferenceTables.importTable,
        controllers.datasets.routes.javascript.CoreferenceTables.deleteTable,
        controllers.datasets.routes.javascript.CoreferenceTables.extractTable,
        controllers.datasets.routes.javascript.CoreferenceTables.applyTable,
      )
    ).as(MimeTypes.JAVASCRIPT)
  }

  def i18n(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(Json.toJson(messagesApi.messages))
  }

  def dashboard(): Action[AnyContent] = WithUserAction.apply { implicit request =>
    Ok(views.html.admin.datasets.dashboard())
  }

  def manager(id: String, ds: Option[String]): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.isVersioned.map { versioned =>
      Ok(views.html.admin.datasets.manager(request.item, versioned))
    }
  }

  def toggleVersioning(id: String, enabled: Boolean): Action[AnyContent] = AdminAction.async { implicit request =>
    storage.setVersioned(enabled).map { _ =>
      Redirect(controllers.datasets.routes.ImportDatasets.manager(id))
    }
  }

  private def countInDataset(id: String, ds: String)(implicit req: RequestHeader): Future[(String, Int)] = {
    import scala.concurrent.duration._
    val pathPrefix: String = prefix(id, ds, FileStage.Input)
    asyncCache.getOrElseUpdate(s"bucket:count:${storage.name}/$pathPrefix", 10.seconds) {
      storage.count(Some(pathPrefix)).map(count => ds -> count)
    }
  }

  def fileCount(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    countInDataset(id, ds).map { case (_, count) => Ok(Json.toJson(count))}
  }

  def stats(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    for {
      dsl <- datasets.list(id)
      idToCount <- Future.sequence(dsl.map(ds => countInDataset(id, ds.id)))
    } yield Ok(Json.toJson(idToCount.toMap))
  }

  def list(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    datasets.list(id).map(dsl => Ok(Json.toJson(dsl)))
  }

  // Permissions that cannot be read (e.g. for a deleted item, or because
  // of a backend error) are treated as not granted, so that one failure
  // does not prevent listing the other repositories.
  private def notGranted(user: UserProfile, id: String): PartialFunction[Throwable, Boolean] = {
    case _: ItemNotFound => false
    case NonFatal(e) =>
      logger.warn(s"Unable to check permissions for user ${user.id} on $id", e)
      false
  }

  // Whether the user can update a repository, and hence manage its datasets.
  // NB: the backend permission calls are cached by the data service, so
  // these are not cached again here.
  private def canUpdate(user: UserProfile, repoId: String): Future[Boolean] =
    withItemPermissions(user, repoId)
      .map(_.hasPermission(ContentTypes.Repository, PermissionType.Update))
      .recover(notGranted(user, repoId))

  // Whether the user can update all repositories in a country, via
  // permissions scoped to it, which is a common way of granting them.
  private def canUpdateWithin(user: UserProfile, countryId: String): Future[Boolean] = {
    implicit val userOpt: Option[UserProfile] = Some(user)
    userDataApi.scopePermissions(user.id, countryId)
      .map(_.has(ContentTypes.Repository, PermissionType.Update))
      .recover(notGranted(user, countryId))
  }

  // The given repositories that the user can update. To minimise backend calls:
  //  - users with global update permission on repositories (whose profile
  //    already includes their global permissions) can update all of them
  //  - users with update permission scoped to a country can update all of
  //    its repositories
  //  - only the remaining repositories are checked individually
  private def updatableRepositories(user: UserProfile, repos: Seq[Repository]): Future[Seq[Repository]] = {
    def filterAsync[T](items: Seq[T])(f: T => Future[Boolean]): Future[Seq[T]] =
      Source(items.toList)
        .mapAsync(16)(item => f(item).map(item -> _))
        .collect { case (item, true) => item }
        .runWith(Sink.seq)

    if (user.isAdmin || user.hasPermission(ContentTypes.Repository, PermissionType.Update))
      Future.successful(repos)
    else for {
      countries <- filterAsync(repos.flatMap(_.country.map(_.id)).distinct)(canUpdateWithin(user, _)).map(_.toSet)
      (inCountries, others) = repos.partition(_.country.exists(c => countries.contains(c.id)))
      individually <- filterAsync(others)(r => canUpdate(user, r.id))
    } yield inCountries ++ individually
  }

  def listAll(): Action[AnyContent] = WithUserAction.async { implicit request =>
    val defaultLogo = controllers.portal.routes.PortalAssets.versioned("img/institution-icon.png").url
    for {
      allSets <- datasets.listAll()
      repos <- userDataApi.fetch[Repository](allSets.keys.toSeq).map(_.flatten)
      updatable <- updatableRepositories(request.user, repos)
      combined = updatable.map { r =>
        RepositoryDatasets(
          r.id,
          r.toStringLang,
          r.allNames.mkString(", "),
          r.data.logoUrl.getOrElse(defaultLogo),
          allSets.getOrElse(r.id, Seq.empty)
        )
      }.sortBy(_.name)
    } yield Ok(Json.toJson(combined))
  }

  def create(id: String): Action[ImportDatasetInfo] = EditAction(id).async(apiJson[ImportDatasetInfo]) { implicit request =>
    checkFondsId(id, request.body) { data =>
      datasets.create(id, data).map { ds =>
        Created(Json.toJson(ds))
      }.recover {
        case e: ImportDatasetExists => BadRequest(e)
      }
    }
  }

  def update(id: String, ds: String): Action[ImportDatasetInfo] = EditAction(id).async(apiJson[ImportDatasetInfo]) { implicit request =>
    checkFondsId(id, request.body) { data =>
      datasets.update(id, ds, data).map { ds =>
        Ok(Json.toJson(ds))
      }
    }
  }

  def batch(id: String): Action[Seq[ImportDatasetInfo]] = EditAction(id).async(apiJson[Seq[ImportDatasetInfo]]) { implicit request =>
    datasets.batch(id, request.body).map { ds =>
      Ok(Json.toJson(ds))
    }
  }

  def delete(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    // Delete all files in stages in the dataset, then the dataset itself...
    val del: Seq[Future[Seq[String]]] = FileStage.values.toSeq
      .map(s => storage.deleteFilesWithPrefix(prefix(id, ds, s)))
    for (_ <- Future.sequence(del); _ <- datasets.delete(id, ds))
      yield NoContent
  }

  def errors(id: String, ds: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    importLogService.errors(id, ds).map { errs =>
      Ok(Json.toJson(errs.map { case (key, err) =>
        key.replace(prefix(id, ds, FileStage.Output), "") -> err
      }))
    }
  }

  private def checkFondsId(id: String, dataset: ImportDatasetInfo)(andThen: ImportDatasetInfo => Future[Result])(implicit userOpt: Option[UserProfile]): Future[Result] = {
    dataset.fonds.map { fondsId =>
      userDataApi.get[DocumentaryUnit](fondsId).flatMap { unit =>
        if (unit.holder.exists(_.id == id)) {
          andThen.apply(dataset)
        } else Future.successful {
          BadRequest(Json.obj("error" -> s"Fonds '$fondsId' not found in repository: $id"))
        }
      }.recover {
        case _: ItemNotFound =>
          BadRequest(Json.obj("error" -> s"Fonds '$fondsId' not found"))
      }
    } getOrElse {
      andThen.apply(dataset)
    }
  }
}
