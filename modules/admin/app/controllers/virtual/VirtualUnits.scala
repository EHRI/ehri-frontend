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
import play.api.mvc.AnyContent
import play.api.data.Form
import play.api.data.Forms._
import solr.facet.FieldFacetClass
import solr.facet.SolrQueryFacet
import solr.facet.QueryFacetClass
import backend.rest.Constants
import scala.concurrent.Future
import models.base.AnyModel
import models.base.Description


@Singleton
case class VirtualUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, idGenerator: IdGenerator,
                                  searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Read[VirtualUnit]
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

  def search = userProfileAction.async { implicit userOpt => implicit request =>
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

  def searchChildren(id: String) = itemPermissionAction.async[VirtualUnit](id) {
      item => implicit userOpt => implicit request =>
    find[VirtualUnit](
      filters = Map(SolrConstants.PARENT_ID -> item.id),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.search(result.page, result.params, result.facets, vuRoutes.search()))
    }
  }

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    find[VirtualUnit](
      filters = Map(SolrConstants.PARENT_ID -> item.id),
      entities = List(EntityType.VirtualUnit),
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.virtualUnit.show(Nil, item, result.page, result.params, result.facets,
          vuRoutes.get(id), annotations, links))
    }
  }

  def getInVc(id: String, pathStr: Option[String]) = userProfileAction.async { implicit userOpt => implicit request =>
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
    } yield Ok(views.html.admin.virtualUnit.showVc(path, item, children.page, children.params, children.facets,
      vuRoutes.getInVc(id, pathStr), annotations, links))
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.virtualUnit.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.virtualUnit.edit(
      item, form.fill(item.model),
      vuRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.virtualUnit.edit(
          olditem, errorForm, vuRoutes.updatePost(id)))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def create = createAction.async { users => groups => implicit userOpt => implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
      Ok(views.html.admin.virtualUnit.create(None, form.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form,
        users, groups, vuRoutes.createPost()))
    }
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.virtualUnit.create(None, errorForm, accForm,
          users, groups, vuRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def createChild(id: String) = childCreateAction.async(id) {
      item => users => groups => implicit userOpt => implicit request =>
    idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
      Ok(views.html.admin.virtualUnit.create(
        Some(item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, vuRoutes.createChildPost(id)))
    }
  }

  def createChildPost(id: String) = childCreatePostAction.async(id, childForm) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.virtualUnit.create(Some(item),
          errorForm, accForm, users, groups,
          vuRoutes.createChildPost(id)))
      }
      case Right(doc) => immediate(Redirect(vuRoutes.get(doc.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def refForm = Form(single(VirtualUnitF.INCLUDE_REF -> nonEmptyText))

  def createChildRef(id: String) = childCreateAction.async(id) {
    item => users => groups => implicit userOpt => implicit request =>
      idGenerator.getNextNumericIdentifier(EntityType.VirtualUnit).map { newId =>
        Ok(views.html.admin.virtualUnit.createRef(
          Some(item), childForm.bind(Map(Entity.IDENTIFIER -> makeId(newId))),
          refForm,
          VisibilityForm.form.fill(item.accessors.map(_.id)),
          users, groups, vuRoutes.createChildRefPost(id)))
      }
  }

  def descriptionRefs: ExtraParams[AnyContent] = { implicit request =>
    refForm.bindFromRequest.fold(
      _ => Map.empty,
      descRef => Map(Constants.ID_PARAM -> Seq(descRef))
    )
  }

  def createChildRefPost(id: String) = childCreatePostAction.async(id, childForm, descriptionRefs) {
    item => formsOrItem => implicit userOpt => implicit request =>
      formsOrItem match {
        case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
          BadRequest(views.html.admin.virtualUnit.createRef(Some(item),
            errorForm, refForm.bindFromRequest, accForm, users, groups,
            vuRoutes.createChildRefPost(id)))
        }
        case Right(doc) => immediate(Redirect(vuRoutes.get(doc.id))
          .flashing("success" -> "item.create.confirmation"))
      }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.admin.delete(
        item, vuRoutes.deletePost(id),
        vuRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { implicit userOpt => implicit request =>
    Redirect(vuRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def createDescription(id: String) = withItemPermission[VirtualUnit](id, PermissionType.Update) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.admin.virtualUnit.createDescription(item,
      descriptionForm, formDefaults, vuRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = createDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.admin.virtualUnit.createDescription(item,
          errorForm, formDefaults, vuRoutes.createDescriptionPost(id)))
      }
      case Right(updated) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def createDescriptionRef(id: String, did: String) = withItemPermission[VirtualUnit](id, PermissionType.Update) {
    item => implicit userOpt => implicit request =>
    ???
  }

  def createDescriptionRefPost(id: String, did: String) = createDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    ???
  }

  def updateDescription(id: String, did: String) = withItemPermission[VirtualUnit](id, PermissionType.Update) {
      item => implicit userOpt => implicit request =>
    itemOr404(item.model.description(did)) { desc =>
      Ok(views.html.admin.virtualUnit.editDescription(item,
        descriptionForm.fill(desc), vuRoutes.updateDescriptionPost(id, did)))
    }
  }

  def updateDescriptionPost(id: String, did: String) = updateDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.admin.virtualUnit.editDescription(item,
          errorForm, vuRoutes.updateDescriptionPost(id, did)))
      }
      case Right(updated) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation")
    }
  }

  def deleteDescription(id: String, did: String) = deleteDescriptionAction(id, did) {
      item => description => implicit userOpt => implicit request =>
    Ok(views.html.admin.deleteDescription(item, description,
      vuRoutes.deleteDescriptionPost(id, did),
      vuRoutes.get(id)))
  }

  def deleteDescriptionPost(id: String, did: String) = deleteDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did) {
      implicit userOpt => implicit request =>
    Redirect(vuRoutes.get(id))
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, vuRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(vuRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(item, perms, sperms,
        vuRoutes.addItemPermissions(id),
        vuRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionItem(item, users, groups,
        vuRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionScope(item, users, groups,
        vuRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionItem(item, accessor, perms, VirtualUnit.Resource.contentType,
        vuRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        vuRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def linkTo(id: String) = withItemPermission[VirtualUnit](id, PermissionType.Annotate) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.admin.virtualUnit.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = linkSelectAction(id, toType) {
      item => page => params => facets => etype => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.linkSourceList(item, page, params, facets, etype,
        vuRoutes.linkAnnotateSelect(id, toType), vuRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.create(target, source,
        Link.form, vuRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.admin.link.create(target, source,
          errorForm, vuRoutes.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }

  def linkMultiAnnotate(id: String) = linkMultiAction(id) {
      target => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.linkMulti(target,
        Link.multiForm, vuRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = linkPostMultiAction(id) {
      formOrAnnotations => implicit userOpt => implicit request =>
    formOrAnnotations match {
      case Left((target,errorForms)) => {
        BadRequest(views.html.admin.link.linkMulti(target,
          errorForms, vuRoutes.linkMultiAnnotatePost(id)))
      }
      case Right(annotations) => {
        Redirect(vuRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }
}


