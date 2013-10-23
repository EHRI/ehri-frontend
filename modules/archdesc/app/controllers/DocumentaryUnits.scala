package controllers.archdesc

import forms.VisibilityForm
import models._
import controllers.base._
import models.forms.LinkForm
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.{Lang, Messages}
import defines._
import views.Helpers
import utils.search.{Dispatcher, SearchParams, FacetSort}
import com.google.inject._
import solr.SolrConstants
import scala.concurrent.Future.{successful => immediate}

@Singleton
class DocumentaryUnits @Inject()(implicit val globalConfig: global.GlobalConfig, val searchDispatcher: Dispatcher) extends EntityRead[DocumentaryUnit]
  with VisibilityController[DocumentaryUnit]
  with CreationContext[DocumentaryUnitF, DocumentaryUnit, DocumentaryUnit]
  with EntityUpdate[DocumentaryUnitF, DocumentaryUnit]
  with EntityDelete[DocumentaryUnit]
  with PermissionScopeController[DocumentaryUnit]
  with EntityAnnotate[DocumentaryUnit]
  with EntityLink[DocumentaryUnit]
  with DescriptionCRUD[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with EntityAccessPoints[DocumentaryUnitDescriptionF, DocumentaryUnitF, DocumentaryUnit]
  with EntitySearch
  with ApiBase[DocumentaryUnit] {

  // Documentary unit facets
  import solr.facet._

  private val entityFacets: FacetBuilder = { implicit lang =>
    List(
      QueryFacetClass(
        key="childCount",
        name=Messages("documentaryUnit.searchInside"),
        param="items",
        render=s => Messages("documentaryUnit." + s),
        facets=List(
          SolrQueryFacet(value = "false", solrValue = "0", name = Some("noChildItems")),
          SolrQueryFacet(value = "true", solrValue = "[1 TO *]", name = Some("hasChildItems"))
        )
      ),
      FieldFacetClass(
        key=IsadG.LANG_CODE,
        name=Messages(IsadG.FIELD_PREFIX + "." + IsadG.LANG_CODE),
        param="lang",
        render=Helpers.languageCodeToName
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("documentaryUnit.heldBy"),
        param="holder",
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key="copyrightStatus",
        name=Messages("copyrightStatus.copyright"),
        param="copyright",
        render=s => Messages("copyrightStatus." + s)
      ),
      FieldFacetClass(
        key="scope",
        name=Messages("scope.scope"),
        param="scope",
        render=s => Messages("scope." + s)
      )
    )
  }


  val targetContentTypes = Seq(ContentTypes.DocumentaryUnit)

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentTypes.DocumentaryUnit

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val descriptionForm = models.forms.IsadGForm.form

  val DEFAULT_SEARCH_PARAMS = SearchParams(entities=List(entityType))

  private val docRoutes = controllers.archdesc.routes.DocumentaryUnits


  def search = Action.async { request =>
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items - UNLESS there's a query, in which case we're
    // going to peer INSIDE items... dodgy logic, maybe...
    
    val filters = if (request.getQueryString(SearchParams.QUERY).isEmpty)
      Map(SolrConstants.TOP_LEVEL -> true) else Map.empty[String,Any]

    searchAction[DocumentaryUnit](filters, defaultParams = Some(DEFAULT_SEARCH_PARAMS),
        entityFacets = entityFacets) {
        page => params => facets => implicit userOpt => implicit request =>
      Ok(views.html.documentaryUnit.search(page, params, facets, docRoutes.search))
    }.apply(request)
  }

  def searchChildren(id: String) = itemPermissionAction.async[DocumentaryUnit](contentType, id) {
      item => implicit userOpt => implicit request =>

    searchAction[DocumentaryUnit](Map("parentId" -> item.id), entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.documentaryUnit.search(page, params, facets, docRoutes.search))
    }.apply(request)
  }

  /*def get(id: String) = getWithChildrenAction(id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>

    Ok(views.html.documentaryUnit.show(
      item, page.copy(items = page.items.map(DocumentaryUnit.apply)), params, annotations, links))
  }*/

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[DocumentaryUnit](Map("parentId" -> item.id),
          defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit))),
          entityFacets = entityFacets) {
        page => params => facets => _ => _ =>
      Ok(views.html.documentaryUnit.show(item, page, params, facets,
          docRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.edit(
      item, form.fill(item.model),
      docRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.documentaryUnit.edit(
          olditem, errorForm, docRoutes.updatePost(id)))
      case Right(item) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(
      item, childForm, VisibilityForm.form, users, groups,
      docRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction.async(id, childForm, contentType) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, accForm, users, groups,
          docRoutes.createDocPost(id)))
      }
      case Right(item) => immediate(Redirect(docRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def createDescription(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.editDescription(item,
        descriptionForm, docRoutes.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = createDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.editDescription(item,
          errorForm, docRoutes.createDescriptionPost(id)))
      }
      case Right(updated) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def updateDescription(id: String, did: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    val desc = item.model.description(did).getOrElse(sys.error("Description not found: " + did))
    Ok(views.html.documentaryUnit.editDescription(item,
      descriptionForm.fill(desc),
      docRoutes.updateDescriptionPost(id, did)))
  }

  def updateDescriptionPost(id: String, did: String) = updateDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.editDescription(item,
          errorForm, docRoutes.updateDescriptionPost(id, did)))
      }
      case Right(updated) => Redirect(docRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def deleteDescription(id: String, did: String) = deleteDescriptionAction(id, did) {
      item => description => implicit userOpt => implicit request =>
    Ok(views.html.deleteDescription(item, description,
        docRoutes.deleteDescriptionPost(id, did),
        docRoutes.get(id)))
  }

  def deleteDescriptionPost(id: String, did: String) = deleteDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, docRoutes.deletePost(id),
        docRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, docRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(docRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        docRoutes.addItemPermissions(id),
        docRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        docRoutes.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        docRoutes.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        docRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        docRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(docRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def linkTo(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: String) = linkSelectAction(id, toType) {
    item => page => params => facets => etype => implicit userOpt => implicit request =>
      Ok(views.html.link.linkSourceList(item, page, params, facets, etype,
          docRoutes.linkAnnotateSelect(id, toType),
          docRoutes.linkAnnotate _))
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.link.link(target, source,
        LinkForm.form, docRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.link.link(target, source,
          errorForm, docRoutes.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(docRoutes.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkMultiAnnotate(id: String) = linkMultiAction(id) {
      target => implicit userOpt => implicit request =>
    Ok(views.html.link.linkMulti(target,
        LinkForm.multiForm, docRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = linkPostMultiAction(id) {
      formOrAnnotations => implicit userOpt => implicit request =>
    formOrAnnotations match {
      case Left((target,errorForms)) => {
        BadRequest(views.html.link.linkMulti(target,
          errorForms, docRoutes.linkMultiAnnotatePost(id)))
      }
      case Right(annotations) => {
        Redirect(docRoutes.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def manageAccessPoints(id: String, descriptionId: String) = manageAccessPointsAction(id, descriptionId) {
      item => desc => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.editAccessPoints(item, desc))
  }
}


