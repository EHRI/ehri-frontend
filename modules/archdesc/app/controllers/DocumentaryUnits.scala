package controllers.archdesc

import _root_.controllers.ListParams
import models.base._
import forms.VisibilityForm
import models._
import controllers.base._
import models.forms.LinkForm
import play.api.libs.concurrent.Execution.Implicits._
import play.api._
import play.api.mvc._
import play.api.i18n.Messages
import controllers.base._
import defines._
import collection.immutable.ListMap
import views.Helpers
import play.api.libs.json.Json
import utils.search.{SearchParams, FacetSort}


object DocumentaryUnits extends EntityRead[DocumentaryUnit]
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

  val DEFAULT_SORT = "name"

  // Documentary unit facets
  import solr.facet._
  override val entityFacets = List(
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


  /**
   * Mapping between incoming list filter parameters
   * and the data values accessed via the server.
   */
  val listFilterMappings: ListMap[String,String] = ListMap(
    "name" -> "name",
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    IsadG.ARCH_HIST -> s"<-describes.${IsadG.ARCH_HIST}",
    IsadG.SCOPE_CONTENT -> s"<-describes.${IsadG.SCOPE_CONTENT}",
    "date" -> s"->hasDate.${DatePeriodF.START_DATE}"
  )

  val orderMappings: ListMap[String,String] = ListMap(
    Entity.IDENTIFIER -> Entity.IDENTIFIER,
    "name" -> "name",
    "date" -> s"->hasDate.${DatePeriodF.START_DATE}"
  )


  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(listFilterMappings, orderMappings, Some(DEFAULT_SORT))
  }

  /**
   * Child list forms are handled the same as the main one
   * @param params
   * @return
   */
  override def processChildParams(params: ListParams) = processParams(params)

  val targetContentTypes = Seq(ContentType.DocumentaryUnit)

  val entityType = EntityType.DocumentaryUnit
  val contentType = ContentType.DocumentaryUnit

  val form = models.forms.DocumentaryUnitForm.form
  val childForm = models.forms.DocumentaryUnitForm.form
  val descriptionForm = models.forms.IsadGForm.form

  val DEFAULT_SEARCH_PARAMS = SearchParams(entities=List(entityType))


  def search = {
    // What filters we gonna use? How about, only list stuff here that
    // has no parent items...
    val filters = Map("depthOfDescription" -> 0)
    searchAction[DocumentaryUnit](filters, defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.documentaryUnit.search(page, params, facets, controllers.archdesc.routes.DocumentaryUnits.search))
    }
  }

  def searchChildren(id: String) = itemPermissionAction[DocumentaryUnit](contentType, id) {
      item => implicit userOpt => implicit request =>

    searchAction[DocumentaryUnit](Map("parentId" -> item.id)) {
      page => params => facets => implicit userOpt => implicit request =>
        Ok(views.html.documentaryUnit.search(page, params, facets, controllers.archdesc.routes.DocumentaryUnits.search))
    }.apply(request)
  }

  /*def get(id: String) = getWithChildrenAction(id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>

    Ok(views.html.documentaryUnit.show(
      item, page.copy(items = page.items.map(DocumentaryUnit.apply)), params, annotations, links))
  }*/

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[DocumentaryUnit](Map("parentId" -> item.id, "depthOfDescription" -> (item.ancestors.size + 1).toString),
          defaultParams = Some(SearchParams(entities = List(EntityType.DocumentaryUnit)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.documentaryUnit.show(item, page, params, facets, controllers.archdesc.routes.DocumentaryUnits.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.edit(
        item, form.fill(item.model),controllers.archdesc.routes.DocumentaryUnits.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.documentaryUnit.edit(
          olditem, errorForm, controllers.archdesc.routes.DocumentaryUnits.updatePost(id)))
      case Right(item) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.create(
      item, childForm, VisibilityForm.form, users, groups, controllers.archdesc.routes.DocumentaryUnits.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction(id, childForm, contentType) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.documentaryUnit.create(item,
          errorForm, accForm, users, groups, controllers.archdesc.routes.DocumentaryUnits.createDocPost(id)))
      }
      case Right(item) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def createDescription(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.editDescription(item,
        descriptionForm, controllers.archdesc.routes.DocumentaryUnits.createDescriptionPost(id)))
  }

  def createDescriptionPost(id: String) = createDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.editDescription(item,
          errorForm, controllers.archdesc.routes.DocumentaryUnits.createDescriptionPost(id)))
      }
      case Right(updated) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def updateDescription(id: String, did: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Update, contentType) {
      item => implicit userOpt => implicit request =>
    val desc = item.model.description(did).getOrElse(sys.error("Description not found: " + did))
    Ok(views.html.documentaryUnit.editDescription(item,
      descriptionForm.fill(desc), controllers.archdesc.routes.DocumentaryUnits.updateDescriptionPost(id, did)))
  }

  def updateDescriptionPost(id: String, did: String) = updateDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did, descriptionForm) {
      item => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => {
        Ok(views.html.documentaryUnit.editDescription(item,
          errorForm, controllers.archdesc.routes.DocumentaryUnits.updateDescriptionPost(id, did)))
      }
      case Right(updated) => Redirect(controllers.archdesc.routes.DocumentaryUnits.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def deleteDescription(id: String, did: String) = deleteDescriptionAction(id, did) {
      item => description => implicit userOpt => implicit request =>
    Ok(views.html.deleteDescription(item, description,
        controllers.archdesc.routes.DocumentaryUnits.deleteDescriptionPost(id, did),
        controllers.archdesc.routes.DocumentaryUnits.get(id)))
  }

  def deleteDescriptionPost(id: String, did: String) = deleteDescriptionPostAction(id, EntityType.DocumentaryUnitDescription, did) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.DocumentaryUnits.get(id))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.archdesc.routes.DocumentaryUnits.deletePost(id),
        controllers.archdesc.routes.DocumentaryUnits.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.DocumentaryUnits.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.archdesc.routes.DocumentaryUnits.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.DocumentaryUnits.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.archdesc.routes.DocumentaryUnits.addItemPermissions(id), controllers.archdesc.routes.DocumentaryUnits.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.archdesc.routes.DocumentaryUnits.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.archdesc.routes.DocumentaryUnits.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.archdesc.routes.DocumentaryUnits.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.archdesc.routes.DocumentaryUnits.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.archdesc.routes.DocumentaryUnits.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def linkTo(id: String) = withItemPermission[DocumentaryUnit](id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.documentaryUnit.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: String) = linkSelectAction(id, toType) {
    item => page => params => facets => etype => implicit userOpt => implicit request =>
      Ok(views.html.linking.linkSourceList(item, page, params, facets, etype,
          controllers.archdesc.routes.DocumentaryUnits.linkAnnotateSelect(id, toType),
          controllers.archdesc.routes.DocumentaryUnits.linkAnnotate _))
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.linking.link(target, source,
        LinkForm.form, controllers.archdesc.routes.DocumentaryUnits.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.linking.link(target, source,
          errorForm, controllers.archdesc.routes.DocumentaryUnits.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(controllers.archdesc.routes.DocumentaryUnits.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkMultiAnnotate(id: String) = linkMultiAction(id) {
      target => implicit userOpt => implicit request =>
    Ok(views.html.linking.linkMulti(target,
        LinkForm.multiForm, controllers.archdesc.routes.DocumentaryUnits.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = linkPostMultiAction(id) {
      formOrAnnotations => implicit userOpt => implicit request =>
    formOrAnnotations match {
      case Left((target,errorForms)) => {
        BadRequest(views.html.linking.linkMulti(target,
          errorForms, controllers.archdesc.routes.DocumentaryUnits.linkMultiAnnotatePost(id)))
      }
      case Right(annotations) => {
        Redirect(controllers.archdesc.routes.DocumentaryUnits.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def manageAccessPoints(id: String, descriptionId: String) = getAction(id) {
      item => annotations => links => implicit userOpt => implicit request =>
    implicit val prefix = IsadG.FIELD_PREFIX
    item.model.description(descriptionId).map { desc =>
      Ok(views.html.documentaryUnit.editAccessPoints(item, desc))
    }.getOrElse {
      NotFound(descriptionId)
    }
  }
}


