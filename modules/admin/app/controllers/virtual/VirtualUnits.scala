package controllers.virtual

import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import models._
import controllers.generic._
import play.api.i18n.Messages
import defines.{ContentTypes,EntityType,PermissionType}
import views.Helpers
import utils.search._
import com.google.inject._
import solr.SolrConstants
import scala.concurrent.Future.{successful => immediate}
import backend.{Entity, IdGenerator, Backend}
import play.api.Play.current
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import solr.facet.FieldFacetClass
import solr.facet.SolrQueryFacet
import solr.facet.QueryFacetClass
import backend.rest.Constants
import scala.concurrent.Future
import models.base.AnyModel
import models.base.Description
import controllers.base.AdminController


@Singleton
case class VirtualUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, idGenerator: IdGenerator,
                                  searchResolver: Resolver, backend: Backend, userDAO: AccountDAO)
  extends AdminController
  with Read[VirtualUnit]
  with Visibility[VirtualUnit]
  with Create[VirtualUnitF,VirtualUnit]
  with Creator[VirtualUnitF, VirtualUnit, VirtualUnit]
  with Update[VirtualUnitF, VirtualUnit]
  with Delete[VirtualUnit]
  with ScopePermissions[VirtualUnit]
  with Annotate[VirtualUnit]
  with Linking[VirtualUnit]
  with Descriptions[DocumentaryUnitDescriptionF, VirtualUnitF, VirtualUnit]
  with Search {

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("virtualUnit.searchInside"),
        param="items",
        render=s => Messages("documentaryUnit." + s),
        facets=List(
          SolrQueryFacet(value = "false", solrValue = "0", name = Some("noChildItems")),
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        )
      ),
      QueryFacetClass(
        key="charCount",
        name=Messages("lod"),
        param="lod",
        render=s => Messages("lod." + s),
        facets=List(
          SolrQueryFacet(value = "low", solrValue = "[0 TO 500]", name = Some("low")),
          SolrQueryFacet(value = "medium", solrValue = "[501 TO 2000]", name = Some("medium")),
          SolrQueryFacet(value = "high", solrValue = "[2001 TO *]", name = Some("high"))
        ),
        sort = FacetSort.Fixed,
        display = FacetDisplay.List
      ),
      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("virtualUnit." + Description.LANG_CODE),
        param="lang",
        render=Helpers.languageCodeToName,
        display = FacetDisplay.Choice
      )
    )
  }

  val formDefaults: Option[Configuration] = current.configuration.getConfig(EntityType.VirtualUnit)

  val targetContentTypes = Seq(ContentTypes.VirtualUnit)

  val form = models.VirtualUnit.form
  val childForm = models.VirtualUnit.form
  val descriptionForm = models.DocumentaryUnitDescription.form

  private def makeId(id: String) = s"vu-$id"

  private val vuRoutes = controllers.virtual.routes.VirtualUnits

  def search = OptionalUserAction.async { implicit request =>
  // What filters we gonna use? How about, only list stuff here that
  // has no parent items - UNLESS there's a query, in which case we're
  // going to peer INSIDE items... dodgy logic, maybe...

    val filters = if (request.getQueryString(SearchParams.QUERY).filterNot(_.trim.isEmpty).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]
    find[VirtualUnit](
      filters = filters,
      entities = List(EntityType.VirtualUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.search(result.page, result.params, result.facets, vuRoutes.search()))
    }
  }

  def searchChildren(id: String) = ItemPermissionAction(id).async { implicit request =>
    find[VirtualUnit](
      filters = Map(SolrConstants.PARENT_ID -> request.item.id),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.search(result.page, result.params, result.facets, vuRoutes.search()))
    }
  }

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    find[VirtualUnit](
      filters = Map(SolrConstants.PARENT_ID -> request.item.id),
      entities = List(EntityType.VirtualUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.show(Nil, request.item, result.page, result.params, result.facets,
          vuRoutes.get(id), request.annotations, request.links))
    }
  }

  def getInVc(id: String, pathStr: Option[String]) = OptionalUserAction.async { implicit request =>
    val pathIds = pathStr.map(_.split(",").toList).getOrElse(List.empty)
    def includedChildren(parent: AnyModel): Future[QueryResult[AnyModel]] = parent match {
      case d: DocumentaryUnit => find[AnyModel](
          filters = Map(SolrConstants.PARENT_ID -> d.id),
          entities = List(d.isA),
          facetBuilder = entityFacets)
      case d: VirtualUnit => d.includedUnits match {
        case other :: _ => includedChildren(other)
        case _ => find[AnyModel](
          filters = Map(SolrConstants.PARENT_ID -> d.id),
          entities = List(d.isA),
          facetBuilder = entityFacets)
      }
      case _ => Future.successful(QueryResult.empty)
    }

    val pathF: Future[List[AnyModel]] = Future.sequence(pathIds.map(pid => backend.getAny[AnyModel](pid)))
    val itemF: Future[AnyModel] = backend.getAny[AnyModel](id)
    val linksF: Future[Seq[Link]] = backend.getLinksForItem[Link](id)
    val annsF: Future[Seq[Annotation]] = backend.getAnnotationsForItem[Annotation](id)
    for {
      item <- itemF
      links <- linksF
      annotations <- annsF
      path <- pathF
      children <- includedChildren(item)
    } yield Ok(views.html.admin.virtualUnit.showVc(
        path, item, children.page, children.params, children.facets,
        vuRoutes.getInVc(id, pathStr), annotations, links))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.virtualUnit.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.edit(
      request.item, form.fill(request.item.model), vuRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.virtualUnit.edit(
          request.item, errorForm, vuRoutes.updatePost(id)))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def create = NewItemAction.async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
      Ok(views.html.admin.virtualUnit.create(None, form.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form,
        request.users, request.groups, vuRoutes.createPost()))
    }
  }

  def createPost = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.virtualUnit.create(None, errorForm, accForm,
          users, groups, vuRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def createChild(id: String) = NewChildAction(id).async { implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
      Ok(views.html.admin.virtualUnit.create(
        Some(request.item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, vuRoutes.createChildPost(id)))
    }
  }

  def createChildPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.virtualUnit.create(Some(request.item),
          errorForm, accForm, users, groups,
          vuRoutes.createChildPost(id)))
      }
      case Right(doc) => immediate(Redirect(vuRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def refForm = Form(single(VirtualUnitF.INCLUDE_REF -> nonEmptyText))

  def createChildRef(id: String) = NewChildAction(id).async { implicit request =>
      idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
        Ok(views.html.admin.virtualUnit.createRef(
          Some(request.item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
          refForm,
          VisibilityForm.form.fill(request.item.accessors.map(_.id)),
          request.users, request.groups, vuRoutes.createChildRefPost(id)))
      }
  }

  def descriptionRefs: ExtraParams = { implicit request =>
    refForm.bindFromRequest.fold(
      _ => Map.empty,
      descRef => Map(Constants.ID_PARAM -> Seq(descRef))
    )
  }

  def createChildRefPost(id: String) = CreateChildAction(id, childForm, descriptionRefs).async { implicit request =>
      request.formOrItem match {
        case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
          BadRequest(views.html.admin.virtualUnit.createRef(Some(request.item),
            errorForm, refForm.bindFromRequest, accForm, users, groups,
            vuRoutes.createChildRefPost(id)))
        }
        case Right(doc) => immediate(Redirect(vuRoutes.get(doc.id))
          .flashing("success" -> "item.create.confirmation"))
      }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, vuRoutes.deletePost(id),
        vuRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(vuRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def createDescription(id: String) = WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.createDescription(request.item,
      descriptionForm, formDefaults, vuRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = CreateDescriptionAction(id, descriptionForm).apply { implicit request =>
    request.formOrDescription match {
      case Left(errorForm) =>
        Ok(views.html.admin.virtualUnit.createDescription(request.item,
          errorForm, formDefaults, vuRoutes.createDescriptionPost(id)))
      case Right(updated) => Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def createDescriptionRef(id: String, did: String) = WithItemPermissionAction(id, PermissionType.Update).apply { implicit request =>
    ???
  }

  def createDescriptionRefPost(id: String, did: String) = CreateDescriptionAction(id, descriptionForm).apply { implicit request =>
    ???
  }

  def updateDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
      Ok(views.html.admin.virtualUnit.editDescription(request.item,
        descriptionForm.fill(request.description), vuRoutes.updateDescriptionPost(id, did)))
    }
  }

  def updateDescriptionPost(id: String, did: String) = {
    UpdateDescriptionAction(id, did, descriptionForm).apply { implicit request =>
      request.formOrDescription match {
        case Left(errorForm) =>
          Ok(views.html.admin.virtualUnit.editDescription(request.item,
            errorForm, vuRoutes.updateDescriptionPost(id, did)))
        case Right(updated) => Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.create.confirmation")
      }
    }
  }

  def deleteDescription(id: String, did: String) = {
    WithDescriptionAction(id, did).apply { implicit request =>
      Ok(views.html.admin.deleteDescription(request.item, request.description,
        vuRoutes.deleteDescriptionPost(id, did), vuRoutes.get(id)))
    }
  }

  def deleteDescriptionPost(id: String, did: String) = {
    DeleteDescriptionAction(id, did).apply { implicit request =>
      Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.delete.confirmation")
    }
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, vuRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        vuRoutes.addItemPermissions(id),
        vuRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        vuRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        vuRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions, VirtualUnit.Resource.contentType,
        vuRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        vuRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.virtualUnit.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = LinkSelectAction(id, toType).apply { implicit request =>
    Ok(views.html.admin.link.linkSourceList(
      request.item, request.page, request.params, request.facets, request.entityType,
        vuRoutes.linkAnnotateSelect(id, toType), vuRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = LinkAction(id, toType, to).apply { implicit request =>
    Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, vuRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = CreateLinkAction(id, toType, to).apply { implicit request =>
    request.formOrLink match {
      case Left((target,errorForm)) =>
        BadRequest(views.html.admin.link.create(request.from, target,
          errorForm, vuRoutes.linkAnnotatePost(id, toType, to)))
      case Right(_) =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkMultiAnnotate(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.link.linkMulti(request.item,
        Link.multiForm, vuRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = CreateMultipleLinksAction(id).apply { implicit request =>
    request.formOrLinks match {
      case Left(errorForms) =>
        BadRequest(views.html.admin.link.linkMulti(request.item,
          errorForms, vuRoutes.linkMultiAnnotatePost(id)))
      case Right(_) =>
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
    }
  }
}


