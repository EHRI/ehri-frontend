package controllers.institutions

import java.io.File
import java.net.URI

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType, PermissionType}
import forms.{FormConfigBuilder, VisibilityForm}
import javax.inject._
import models._
import net.coobird.thumbnailator.Thumbnails
import net.coobird.thumbnailator.tasks.UnsupportedFormatException
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc._
import services.data.DataHelpers
import services.ingest.{EadValidator, IngestApi, IngestParams}
import services.search._
import services.storage.{DOFileStorage, FileStorage}
import utils.{PageParams, RangeParams}
import views.Helpers

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Repositories @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  fileStorage: FileStorage,
  eadValidator: EadValidator
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository]
  with Delete[Repository]
  with Creator[DocumentaryUnit, Repository]
	with Visibility[Repository]
  with ScopePermissions[Repository]
  with Annotate[Repository]
  with Linking[Repository]
  with SearchType[Repository]
  with Search {

  // Documentary unit facets
  private val repositoryFacets: FacetBuilder = { implicit request =>
    import SearchConstants._
    List(
      QueryFacetClass(
        key = CHILD_COUNT,
        name = Messages("repository.itemsHeldOnline"),
        param = "data",
        render = s => Messages("repository.itemsHeldOnline." + s),
        facets = List(
          QueryFacet(value = "false", range = Val("0")),
          QueryFacet(value = "true", range = Val("1").to(End))
        )
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository.countryCode"),
        param = "country",
        render = Helpers.countryCodeToName,
        display = FacetDisplay.DropDown,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key = "priority",
        name = Messages("facet.priority"),
        param = "priority",
        sort = FacetSort.Name,
        display = FacetDisplay.Choice,
        render = {
          case s if s == "0" => Messages("repository.priority.zero")
          case s if s == "1" => Messages("repository.priority.one")
          case s if s == "2" => Messages("repository.priority.two")
          case s if s == "3" => Messages("repository.priority.three")
          case s if s == "4" => Messages("repository.priority.four")
          case s if s == "5" => Messages("repository.priority.five")
          case s if s == "-1" => Messages("repository.priority.reject")
          case _ => Messages("repository.priority.unknown")
        }
      )
    )
  }

  override protected val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  private val formConfig: FormConfigBuilder = FormConfigBuilder(EntityType.Repository, config)
  private val form = models.Repository.form
  private val childFormDefaults: FormConfigBuilder = FormConfigBuilder(EntityType.DocumentaryUnit, config)
  private val childForm = models.DocumentaryUnit.form
  private val repositoryRoutes = controllers.institutions.routes.Repositories


  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging, facetBuilder = repositoryFacets).apply { implicit request =>
      Ok(views.html.admin.repository.search(request.result, repositoryRoutes.search()))
    }

  /**
   * Search documents inside repository.
   */
  def get(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    val filters = (if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true)
      else Map.empty[String,Any]) ++ Map(SearchConstants.HOLDER_ID -> request.item.id)

    findType[DocumentaryUnit](params, paging, filters = filters,
      facetBuilder = repositoryFacets, sort = SearchSort.Id).map { result =>
      Ok(views.html.admin.repository.show(request.item, result,
        repositoryRoutes.get(id), request.annotations, request.links))
        .withPreferences(preferences.withRecentItem(id))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.repository.list(request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.edit(request.item,
      form.fill(request.item.data), formConfig.forUpdate, repositoryRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.repository.edit(
          request.item, errorForm, formConfig.forUpdate, repositoryRoutes.updatePost(id)))
      case Right(doc) => Redirect(repositoryRoutes.get(doc.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(request.item, childForm, childFormDefaults.forCreate,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, repositoryRoutes.createDocPost(id)))
  }

  def createDocPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm, usersAndGroups)) =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, childFormDefaults.forCreate, accForm,
          usersAndGroups, repositoryRoutes.createDocPost(id)))
      case Right(citem) => Redirect(controllers.units.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(request.item, repositoryRoutes.deletePost(id),
        repositoryRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(repositoryRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, repositoryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(repositoryRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
          repositoryRoutes.addItemPermissions(id), repositoryRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
        repositoryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.usersAndGroups,
        repositoryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        repositoryRoutes.setItemPermissionsPost(id, userType, userId)))
    }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        repositoryRoutes.setScopedPermissionsPost(id, userType, userId)))
    }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] =
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }

  def linkTo(id: String): Action[AnyContent] = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.repository.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        repositoryRoutes.linkAnnotateSelect(id, toType),
        (other, copy) => repositoryRoutes.linkAnnotate(id, toType, other, copy)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.formWithCopyOptions(copy, request.from, request.to),
        repositoryRoutes.linkAnnotatePost(id, toType, to, copy), copy))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String, copy: Boolean): Action[AnyContent] =
    CreateLinkAction(id, toType, to).apply { implicit request =>
      request.formOrLink match {
        case Left((target,errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, repositoryRoutes.linkAnnotatePost(id, toType, to, copy), copy))
        case Right(_) =>
          Redirect(repositoryRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }

  def updateIndex(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    Ok(views.html.admin.search.updateItemIndex(request.item, field = SearchConstants.HOLDER_ID,
      action = controllers.admin.routes.Indexing.indexer()))
  }

  def ingest(id: String, sync: Boolean): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    val dataType = if (sync) IngestApi.IngestDataType.EadSync else IngestApi.IngestDataType.Ead
    Ok(views.html.admin.tools.ingest(request.item, None, IngestParams.ingestForm, dataType,
      controllers.admin.routes.Ingest.ingestPost(ContentTypes.Repository, id, dataType), sync = sync))
  }

  import play.api.data.Form
  import play.api.data.Forms._

  private val imageForm = Form(
    single("image" -> text)
  )

  def updateLogoImage(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.editLogo(request.item, imageForm,
      controllers.institutions.routes.Repositories.updateLogoImagePost(id)))
  }

  // Body parser that'll refuse anything larger than 5MB
  private def uploadParser = parsers.maxLength(
    config.get[Int]("ehri.portal.profile.maxImageSize"), parsers.multipartFormData)

  def updateLogoImagePost(id: String): Action[Either[MaxSizeExceeded, MultipartFormData[TemporaryFile]]] = EditAction(id).async(uploadParser) { implicit request =>

    def onError(err: String, status: Status = BadRequest): Future[Result] = immediate(
      status(views.html.admin.repository.editLogo(request.item, imageForm.withGlobalError(err),
        repositoryRoutes.updateLogoImagePost(id))))

    request.body match {
      case Left(MaxSizeExceeded(length)) => onError("errors.imageTooLarge", EntityTooLarge)
      case Right(multipartForm) => multipartForm.file("image").map { file =>
        if (isValidContentType(file)) {
          try {
            for {
              url <- convertAndUploadFile(file, request.item, request)
              _ <- userDataApi.patch[Repository](
                request.item.id,
                Json.obj(RepositoryF.LOGO_URL -> url),
                logMsg = getLogMessage
              )
            } yield Redirect(repositoryRoutes.get(id))
              .flashing("success" -> "item.update.confirmation")
          } catch {
            case e: UnsupportedFormatException => onError("errors.badFileType")
          }
        } else {
          onError("errors.badFileType")
        }
      }.getOrElse {
        onError("errors.noFileGiven")
      }
    }
  }

  private def isValidContentType(file: FilePart[TemporaryFile]): Boolean =
    file.contentType.exists(_.toLowerCase.startsWith("image/"))

  private def convertAndUploadFile(file: FilePart[TemporaryFile], repo: Repository, request: RequestHeader): Future[String] = {
    val instance = config.getOptional[String]("storage.instance").getOrElse(request.host)
    val classifier = config.get[String]("storage.institutions.classifier")
    val extension = file.filename.substring(file.filename.lastIndexOf("."))
    val storeName = s"images/$instance/${repo.id}$extension"
    val temp = File.createTempFile(repo.id, extension)
    Thumbnails.of(file.ref.path.toFile).size(200, 200).toFile(temp)

    val url: Future[String] = fileStorage.putFile(classifier, storeName, temp, public = true).map(_.toString)
    url.onComplete { _ => temp.delete() }
    url
  }

  private val fileForm = Form(single("files" -> text))
  private val storage = DOFileStorage(config)(mat.system, mat)
  private val bucket = "ehri-assets"
  private val prefix: String => String = id => s"ingest/$id/"

  def validateEad(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.validateEad(Map.empty[String, Seq[EadValidator#Error]], request.item, fileForm,
      controllers.institutions.routes.Repositories.validateEadPost(id)))
  }

  def validateEadPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asMultipartFormData.map { data =>
      val results: Seq[Future[(String, Seq[EadValidator#Error])]] = data.files.map { file =>
        eadValidator.validateEad(file.ref.toPath).map(errs => file.filename -> errs)
      }

      Future.sequence(results).map { out =>
        Ok(views.html.admin.repository.validateEad(out.toMap, request.item, fileForm,
          controllers.institutions.routes.Repositories.validateEadPost(id)))
      }
    }.getOrElse {
      immediate(Redirect(repositoryRoutes.validateEad(id)))
    }
  }

  def uploadData(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    storage.listFiles(bucket, prefix = Some(prefix(id))).runWith(Sink.seq).map  { files =>
      val stripPrefix = files.map(f => f.copy(key = f.key.replaceFirst(prefix(id), "") ))
      Ok(views.html.admin.repository.uploadData(stripPrefix, request.item, fileForm,
        controllers.institutions.routes.Repositories.uploadDataPost(id)))
    }
  }

  def uploadDataPost(id: String): Action[AnyContent] = EditAction(id).async { implicit request =>
    request.body.asMultipartFormData.map { data =>

      val uris: Seq[Future[URI]] = data.files.map { file =>
        val path = s"${prefix(id)}${file.filename}"
        storage.putFile(bucket, path, file.ref.path.toFile)
      }
      Future.sequence(uris).map { _ =>
        Redirect(repositoryRoutes.uploadData(id))
          .flashing("success" -> "That worked!")
      }
    }.getOrElse {
      immediate(Redirect(repositoryRoutes.uploadData(id)))
    }
  }
}
