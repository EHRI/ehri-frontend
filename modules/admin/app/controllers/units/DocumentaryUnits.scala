package controllers.units

import auth.AccountManager
import backend.rest.cypher.Cypher
import play.api.cache.CacheApi
import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import models._
import controllers.generic._
import play.api.i18n.{MessagesApi, Messages}
import defines.{ContentTypes,EntityType,PermissionType}
import play.api.libs.iteratee.Enumerator
import utils.MovedPageLookup
import views.{MarkdownRenderer, Helpers}
import utils.search._
import javax.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import play.api.Configuration
import controllers.base.AdminController


@Singleton
case class DocumentaryUnits @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  searchEngine: SearchEngine,
  searchResolver: SearchItemResolver,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends AdminController
  with Read[DocumentaryUnit]
  with Visibility[DocumentaryUnit]
  with Creator[DocumentaryUnitF, DocumentaryUnit, DocumentaryUnit]
  with Update[DocumentaryUnitF, DocumentaryUnit]
  with Delete[DocumentaryUnit]
  with ScopePermissions[DocumentaryUnit]
  with Annotate[DocumentaryUnit]
  with Linking[DocumentaryUnit]
  with Descriptions[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with AccessPoints[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with Search {

  // Documentary unit facets
  import SearchConstants._
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = IS_PARENT,
        name = Messages("facet.parent"),
        param = "parent",
        render = s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = LANGUAGE_CODE,
        name = Messages("documentaryUnit." + LANGUAGE_CODE),
        param = "lang",
        render = Helpers.languageCodeToName,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = CREATION_PROCESS,
        name = Messages("facet.source"),
        param = "source",
        render = s => Messages("facet.source." + s),
        sort = FacetSort.Name,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = HOLDER_NAME,
        name = Messages("documentaryUnit.heldBy"),
        param = "holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key = COUNTRY_CODE,
        name = Messages("repository." + COUNTRY_CODE),
        param = "country",
        render = (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      QueryFacetClass(
        key = CHAR_COUNT,
        name = Messages("lod"),
        param = "lod",
        render = s => Messages("facet.lod." + s),
        facets = List(
          QueryFacet(value = "low", range = Start to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key = "scope",
        name = Messages("scope.scope"),
        param = "scope",
        render = s => Messages("scope." + s)
      )
    )
  }

  val formDefaults: Option[Configuration] = app.configuration.getConfig(EntityType.DocumentaryUnit)

  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  val form = models.DocumentaryUnit.form
  val childForm = models.DocumentaryUnit.form
  val descriptionForm = models.DocumentaryUnitDescription.form

  private val docRoutes = controllers.units.routes.DocumentaryUnits


  def search = OptionalUserAction.async { implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    
    val filters = if (!hasActiveQuery(request))
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    findType[DocumentaryUnit](
      filters = filters,
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def searchChildren(id: String) = ItemPermissionAction(id).async { implicit request =>
    val filterKey = if (!hasActiveQuery(request)) SearchConstants.PARENT_ID
      else SearchConstants.ANCESTOR_IDS

    findType[DocumentaryUnit](
      filters = Map(filterKey -> request.item.id),
      facetBuilder = entityFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    findType[DocumentaryUnit](
      filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      facetBuilder = entityFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.show(request.item, result,
          docRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.edit(
      request.item, form.fill(request.item.model), docRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.documentaryUnit.edit(
          request.item, errorForm, docRoutes.updatePost(id)))
      case Right(item) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createDoc(id: String) = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.create(
      request.item, childForm, formDefaults, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, docRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.documentaryUnit.create(request.item,
          errorForm, formDefaults, accForm, users, groups,
          docRoutes.createDocPost(id)))
      }
      case Right(doc) => immediate(Redirect(docRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def createDescription(id: String) = WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.createDescription(request.item,
        descriptionForm, formDefaults, docRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = {
    CreateDescriptionAction(id, descriptionForm).apply { implicit request =>
      request.formOrDescription match {
        case Left(errorForm) =>
          Ok(views.html.admin.documentaryUnit.createDescription(request.item,
            errorForm, formDefaults, docRoutes.createDescriptionPost(id)))
        case Right(_) => Redirect(docRoutes.get(id))
          .flashing("success" -> "item.create.confirmation")
      }
    }
  }

  def updateDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
        Ok(views.html.admin.documentaryUnit.editDescription(request.item,
          descriptionForm.fill(request.description),
          docRoutes.updateDescriptionPost(id, did)))
    }
  }

  def updateDescriptionPost(id: String, did: String) = {
    UpdateDescriptionAction(id, did, descriptionForm).apply { implicit request =>
      request.formOrDescription match {
        case Left(errorForm) =>
          Ok(views.html.admin.documentaryUnit.editDescription(request.item,
            errorForm, docRoutes.updateDescriptionPost(id, did)))
        case Right(_) => Redirect(docRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }

  def deleteDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
      Ok(views.html.admin.deleteDescription(request.item, request.description,
        docRoutes.deleteDescriptionPost(id, did), docRoutes.get(id)))
    }
  }

  def deleteDescriptionPost(id: String, did: String) = {
    DeleteDescriptionAction(id, did).apply { implicit request =>
      Redirect(docRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, docRoutes.deletePost(id), docRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(docRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, docRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(docRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        docRoutes.addItemPermissions(id),
        docRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        docRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        docRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        docRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        docRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.documentaryUnit.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = LinkSelectAction(id, toType).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
          docRoutes.linkAnnotateSelect(id, toType),
          docRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = LinkAction(id, toType, to).apply { implicit request =>
    Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, docRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = CreateLinkAction(id, toType, to).apply { implicit request =>
    request.formOrLink match {
      case Left((target,errorForm)) =>
        BadRequest(views.html.admin.link.create(request.from, target,
          errorForm, docRoutes.linkAnnotatePost(id, toType, to)))
      case Right(_) =>
        Redirect(docRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkMultiAnnotate(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.link.linkMulti(request.item,
        Link.multiForm, docRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = CreateMultipleLinksAction(id).apply { implicit request =>
    request.formOrLinks match {
      case Left(errorForms) =>
        BadRequest(views.html.admin.link.linkMulti(request.item,
          errorForms, docRoutes.linkMultiAnnotatePost(id)))
      case Right(_) =>
        Redirect(docRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }

  def manageAccessPoints(id: String, descriptionId: String) = {
    WithDescriptionAction(id, descriptionId).apply { implicit request =>
      Ok(views.html.admin.documentaryUnit.editAccessPoints(request.item, request.description))
    }
  }

  def exportEad(id: String) = OptionalUserAction.async { implicit request =>
    val params = request.queryString.filterKeys(_ == "lang")
    userBackend.stream(s"${EntityType.DocumentaryUnit}/$id/ead", params = params).map { case (head, body) =>
      Status(head.status)
        .chunked(body.andThen(Enumerator.eof))
        .withHeaders(head.headers.map(s => (s._1, s._2.head)).toSeq: _*)
    }
  }
}


