package controllers.institutions

import org.apache.pekko.stream.Materializer
import controllers.AppComponents
import controllers.base.{AdminController, ImageHelpers, ResolutionTooHigh, UnrecognizedType}
import controllers.generic._
import forms._
import models.{ContentTypes, EntityType, _}
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.libs.json.Json
import play.api.mvc._
import services.data.DataHelpers
import services.ingest.{EadValidator, IngestService}
import services.search._
import services.storage.FileStorage
import utils.{PageParams, RangeParams}
import views.Helpers

import javax.inject._
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Repositories @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  fileStorage: FileStorage,
  eadValidator: EadValidator,
)(
  implicit mat: Materializer
) extends AdminController
  with Read[Repository]
  with Update[Repository]
  with Delete[Repository]
  with DeleteChildren[DocumentaryUnit, Repository]
  with Creator[DocumentaryUnit, Repository]
	with Visibility[Repository]
  with ScopePermissions[Repository]
  with Annotate[Repository]
  with Linking[Repository]
  with SearchType[Repository]
  with Search
  with ImageHelpers {

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
      ),
      FieldFacetClass(
        key = RESTRICTED_FIELD,
        name = Messages("facet.restricted"),
        param = "restricted",
        render = s => Messages("facet.restricted." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
    )
  }

  override protected val targetContentTypes: Seq[ContentTypes.Value] = Seq(ContentTypes.DocumentaryUnit)
  private val form = models.Repository.form
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
    val filters = (if (!hasActiveQuery(request)) Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]) ++
        Map(SearchConstants.HOLDER_ID -> request.item.id)

    for {
      result <- findType[DocumentaryUnit](
        params,
        paging,
        filters = filters,
        facetBuilder = repositoryFacets,
        sort = SearchSort.Id)
      fieldMetadata <- entityTypeMetadata.listEntityTypeFields(EntityType.Repository)
       advisories = fieldMetadata.validate(request.item.data)
    } yield {
      if(isAjax) Ok(views.html.admin.search.inlineItemList(result = result))
        .withHeaders("more" -> result.page.hasMore.toString)
      else Ok(views.html.admin.repository.show(request.item, result,
        repositoryRoutes.get(id), request.annotations, request.links, advisories))
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
      form.fill(request.item.data), request.fieldHints, repositoryRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.repository.edit(
          request.item, errorForm, request.fieldHints, repositoryRoutes.updatePost(id)))
      case Right(doc) => Redirect(repositoryRoutes.get(doc.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(request.item, childForm,
      visibilityForm.fill(request.item.accessors.map(_.id)), request.fieldHints,
      request.usersAndGroups, repositoryRoutes.createDocPost(id)))
  }

  def createDocPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).apply { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm, fieldHints, usersAndGroups)) =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, accForm, fieldHints,
          usersAndGroups, repositoryRoutes.createDocPost(id)))
      case Right(citem) => Redirect(controllers.units.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def delete(id: String, params: PageParams): Action[AnyContent] = CheckDeleteAction(id).async { implicit request =>
    userDataApi.children[Repository, DocumentaryUnit](id, params).map { children =>
      Ok(views.html.admin.deleteParent(
        request.item, children,
        repositoryRoutes.deletePost(id),
        cancel = repositoryRoutes.get(id),
        deleteChild = cid => controllers.units.routes.DocumentaryUnits.delete(cid),
        deleteAll = Some(repositoryRoutes.deleteContents(id)),
        breadcrumbs = views.html.admin.repository.breadcrumbs(request.item)))
    }
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(repositoryRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def deleteContents(id: String, params: PageParams): Action[AnyContent] = CheckDeleteChildrenAction(id, params).apply { implicit request =>
    Ok(views.html.admin.deleteChildren(
      request.item,
      request.children,
      DeleteChildrenOptions.form,
      repositoryRoutes.deleteContentsPost(id),
      cancel = repositoryRoutes.get(id),
      breadcrumbs = views.html.admin.repository.breadcrumbs(request.item)))
  }

  def deleteContentsPost(id: String, params: PageParams): Action[AnyContent] = DeleteChildrenAction(id, params).apply { implicit request =>
    request.formOrIds match {
      case Left((errForm, children)) =>
          BadRequest(views.html.admin.deleteChildren(
            request.item,
            children,
            errForm,
            repositoryRoutes.deleteContentsPost(id),
            cancel = repositoryRoutes.get(id),
            breadcrumbs = views.html.admin.repository.breadcrumbs(request.item)))
      case Right(ids) =>
        Redirect(repositoryRoutes.get(id))
          .flashing("success" -> Messages("item.deleteChildren.confirmation", ids.size))
    }
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      visibilityForm.fill(request.item.accessors.map(_.id)),
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
      action = controllers.admin.routes.Indexing.indexer(), views.html.admin.repository.breadcrumbs(request.item)))
  }

  def ingest(id: String, sync: Boolean): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    val dataType = if (sync) IngestService.IngestDataType.EadSync else IngestService.IngestDataType.Ead
    Ok(views.html.admin.ingest.ingest(request.item, None, IngestParams.ingestForm, dataType,
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
    config.underlying.getBytes("ehri.portal.profile.maxImageSize"), parsers.multipartFormData)

  def updateLogoImagePost(id: String): Action[Either[MaxSizeExceeded, MultipartFormData[TemporaryFile]]] = EditAction(id).async(uploadParser) { implicit request =>

    def onError(err: String, status: Status = BadRequest): Future[Result] = immediate(
      status(views.html.admin.repository.editLogo(request.item, imageForm.withGlobalError(err),
        repositoryRoutes.updateLogoImagePost(id))))

    request.body match {
      case Left(MaxSizeExceeded(_)) => onError("errors.imageTooLarge", EntityTooLarge)
      case Right(multipartForm) =>
        (for {
          url <- convertAndUploadFile(multipartForm.file("image"), request.item)
          _ <- userDataApi.patch[Repository](request.item.id, Json.obj(RepositoryF.LOGO_URL -> url), logMsg = getLogMessage)
        } yield Redirect(repositoryRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
          ).recoverWith {
          case _: UnrecognizedType => onError("errors.badFileType")
          case _: ResolutionTooHigh => onError("errors.imageResolutionTooLarge")
        }
    }
  }
}
