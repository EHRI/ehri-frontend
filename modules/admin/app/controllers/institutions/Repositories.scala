package controllers.institutions

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import controllers.generic._
import models._
import play.api.i18n.{MessagesApi, Messages}
import defines.{PermissionType, EntityType, ContentTypes}
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{Result, ResponseHeader}
import utils.MovedPageLookup
import views.{MarkdownRenderer, Helpers}
import utils.search._
import javax.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import play.api.Configuration
import controllers.base.AdminController


@Singleton
case class Repositories @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchIndexer: SearchIndexMediator,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends AdminController
  with Read[Repository]
  with Update[RepositoryF, Repository]
  with Delete[Repository]
  with Creator[DocumentaryUnitF,DocumentaryUnit, Repository]
	with Visibility[Repository]
  with ScopePermissions[Repository]
  with Annotate[Repository]
  with Linking[Repository]
  with SearchType[Repository]
  with Search
  with Indexable[Repository] {

  // Documentary unit facets
  private val repositoryFacets: FacetBuilder = { implicit request =>
    val prefix = EntityType.Repository.toString
    import SearchConstants._
    List(
      QueryFacetClass(
        key = CHILD_COUNT,
        name = Messages(prefix + ".itemsHeldOnline"),
        param = "data",
        render = s => Messages(prefix + "." + s),
        facets = List(
          QueryFacet(value = "false", range = Val("0"), name = Some("noChildItems")),
          QueryFacet(value = "true", range = Val("1"), name = Some("hasChildItems"))
        )
      ),
      QueryFacetClass(
        key = CHAR_COUNT,
        name = Messages("facet.lod"),
        param = "lod",
        render = s => Messages("facet.lod." + s),
        facets = List(
          QueryFacet(value = "low", range = Val("0") to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
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
  }

  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  private val form = models.Repository.form

  private val childFormDefaults: Option[Configuration] = app.configuration.getConfig(EntityType.DocumentaryUnit)

  private val childForm = models.DocumentaryUnit.form

  private val repositoryRoutes = controllers.institutions.routes.Repositories


  def search = SearchTypeAction(facetBuilder = repositoryFacets).apply { implicit request =>
    Ok(views.html.admin.repository.search(request.result, repositoryRoutes.search()))
  }

  /**
   * Search documents inside repository.
   */
  def get(id: String) = ItemMetaAction(id).async { implicit request =>

    val filters = (if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true)
      else Map.empty[String,Any]) ++ Map(SearchConstants.HOLDER_ID -> request.item.id)

    findType[DocumentaryUnit](
      filters = filters,
      facetBuilder = repositoryFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.admin.repository.show(request.item, result,
        repositoryRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.repository.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.repository.edit(request.item,
      form.fill(request.item.model), repositoryRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.repository.edit(
          request.item, errorForm, repositoryRoutes.updatePost(id)))
      case Right(doc) => Redirect(repositoryRoutes.get(doc.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String) = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(request.item, childForm, childFormDefaults,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, repositoryRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, childFormDefaults, accForm,
          users, groups, repositoryRoutes.createDocPost(id)))
      }
      case Right(citem) => immediate(Redirect(controllers.units.routes.DocumentaryUnits.get(citem.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(request.item, repositoryRoutes.deletePost(id),
        repositoryRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(repositoryRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, repositoryRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(repositoryRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        repositoryRoutes.addItemPermissions(id), repositoryRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        repositoryRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        repositoryRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        repositoryRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        repositoryRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(repositoryRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.repository.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = LinkSelectAction(id, toType).apply { implicit request =>
    Ok(views.html.admin.link.linkSourceList(
      request.item, request.searchResult, request.entityType,
      repositoryRoutes.linkAnnotateSelect(id, toType),
      repositoryRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = LinkAction(id, toType, to).apply { implicit request =>
    Ok(views.html.admin.link.create(request.from, request.to,
      Link.form, repositoryRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = CreateLinkAction(id, toType, to).apply { implicit request =>
    request.formOrLink match {
      case Left((target,errorForm)) =>
        BadRequest(views.html.admin.link.create(request.from, target,
          errorForm, repositoryRoutes.linkAnnotatePost(id, toType, to)))
      case Right(_) =>
        Redirect(repositoryRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }

  def updateIndex(id: String) = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
    Ok(views.html.admin.search.updateItemIndex(request.item,
      action = controllers.institutions.routes.Repositories.updateIndexPost(id)))
  }

  def updateIndexPost(id: String) = updateChildItemsPost(SearchConstants.HOLDER_ID, id)

  def exportEag(id: String) = OptionalUserAction.async { implicit request =>
    val params = request.queryString.filterKeys(_ == "lang")
    userBackend.stream(s"${EntityType.Repository}/$id/eag", params = params).map { case (head, body) =>
      Status(head.status)
        .chunked(body.andThen(Enumerator.eof))
        .withHeaders(head.headers.map(s => (s._1, s._2.head)).toSeq: _*)
    }
  }
}
