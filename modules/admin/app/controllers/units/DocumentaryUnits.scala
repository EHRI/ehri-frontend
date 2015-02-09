package controllers.units

import auth.AccountManager
import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import models._
import controllers.generic._
import play.api.i18n.Messages
import defines.{ContentTypes,EntityType,PermissionType}
import views.Helpers
import utils.search._
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.{ApiUser, Backend}
import play.api.Play.current
import play.api.Configuration
import play.api.http.MimeTypes
import utils.ead.EadExporter
import models.base.Description
import controllers.base.AdminController


@Singleton
case class DocumentaryUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager)
  extends AdminController
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
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key="isParent",
        name=Messages("facet.parent"),
        param="parent",
        render=s => Messages("facet.parent." + s),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("documentaryUnit." + Description.LANG_CODE),
        param="lang",
        render=Helpers.languageCodeToName,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="creationProcess",
        name=Messages("facet.source"),
        param="source",
        render=s => Messages("facet.source." + s),
        sort = FacetSort.Name,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="countryCode",
        name=Messages("repository.countryCode"),
        param="country",
        render= (s: String) => Helpers.countryCodeToName(s),
        sort = FacetSort.Name,
        display = FacetDisplay.DropDown
      ),
      FieldFacetClass(
        key="copyrightStatus",
        name=Messages("copyrightStatus.copyright"),
        param="copyright",
        render=s => Messages("copyrightStatus." + s)
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("lod"),
        param="lod",
        render=s => Messages("lod." + s),
        facets=List(
          QueryFacet(value = "low", range = Start to Val("500")),
          QueryFacet(value = "medium", range = Val("501") to Val("2000")),
          QueryFacet(value = "high", range = Val("2001") to End)
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key="scope",
        name=Messages("scope.scope"),
        param="scope",
        render=s => Messages("scope." + s)
      )
    )
  }

  val formDefaults: Option[Configuration] = current.configuration.getConfig(EntityType.DocumentaryUnit)

  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  val form = models.DocumentaryUnit.form
  val childForm = models.DocumentaryUnit.form
  val descriptionForm = models.DocumentaryUnitDescription.form

  private val docRoutes = controllers.units.routes.DocumentaryUnits


  def search = OptionalUserAction.async { implicit request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    
    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SearchConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    find[DocumentaryUnit](
      filters = filters,
      entities=List(EntityType.DocumentaryUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def searchChildren(id: String) = ItemPermissionAction(id).async { implicit request =>
    find[DocumentaryUnit](
      filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      facetBuilder = entityFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.search(
        result,
        docRoutes.search()))
    }
  }

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    find[DocumentaryUnit](
      filters = Map(SearchConstants.PARENT_ID -> request.item.id),
      entities = List(EntityType.DocumentaryUnit),
      facetBuilder = entityFacets,
      defaultOrder = SearchOrder.Id
    ).map { result =>
      Ok(views.html.admin.documentaryUnit.show(request.item, result,
          docRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
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
        DocumentaryUnit.Resource.contentType, docRoutes.setItemPermissionsPost(id, userType, userId)))
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

  def exportEad(id: String) = OptionalAuthAction.async { implicit authRequest =>
    val eadId: String = docRoutes.exportEad(id).absoluteURL(globalConfig.https)

    EadExporter(backend).exportEad(id, eadId).map { xml =>
      Ok(xml).as(MimeTypes.XML)
    }
  }
}


