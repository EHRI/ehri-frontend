package controllers.guides

import _root_.forms.VisibilityForm
import models._
import controllers.generic._
import play.api.mvc._
import play.api.i18n.Messages
import defines.{ContentTypes,EntityType,PermissionType}
import views.Helpers
import utils.search._
import com.google.inject._
import solr.SolrConstants
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import play.api.Play.current
import play.api.Configuration


@Singleton
case class VirtualUnits @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Read[VirtualUnit]
  with Visibility[VirtualUnit]
  with Create[VirtualUnitF,VirtualUnit]
  with Creator[VirtualUnitF, VirtualUnit, VirtualUnit]
  with Update[VirtualUnitF, VirtualUnit]
  with Delete[VirtualUnit]
  with ScopePermissions[VirtualUnit]
  with Annotate[VirtualUnit]
  with Linking[VirtualUnit]
  with Api[VirtualUnit] {

  implicit val resource = VirtualUnit.Resource

  val formDefaults: Option[Configuration] = current.configuration.getConfig(EntityType.VirtualUnit)
  val descDefaults: Option[Configuration] = formDefaults.flatMap(_.getConfig(IsadG.FIELD_PREFIX))

  val contentType = ContentTypes.VirtualUnit
  val targetContentTypes = Seq(ContentTypes.VirtualUnit)

  val form = models.VirtualUnit.form
  val childForm = models.VirtualUnit.form

  private val vuRoutes = controllers.guides.routes.VirtualUnits


  /*def get(id: String) = getWithChildrenAction(id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>

    Ok(views.html.virtualUnit.show(
      item, page.copy(items = page.items.map(VirtualUnit.apply)), params, annotations, links))
  }*/

  def get(id: String) = getWithChildrenAction[VirtualUnit](id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.show(item, page, params, annotations, links))
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.list(page, params))
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.edit(
      item, form.fill(item.model),
      vuRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.virtualUnit.edit(
          olditem, errorForm, vuRoutes.updatePost(id)))
      case Right(item) => Redirect(vuRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.create(None, form, VisibilityForm.form, users, groups, vuRoutes.createPost()))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.virtualUnit.create(None, errorForm, accForm, users, groups, vuRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vuRoutes.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id)))
    }
  }

  def createDoc(id: String) = childCreateAction(id, contentType) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.create(
      Some(item), childForm, VisibilityForm.form, users, groups,
      vuRoutes.createDocPost(id)))
  }

  def createDocPost(id: String) = childCreatePostAction.async(id, childForm, contentType) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.virtualUnit.create(Some(item),
          errorForm, accForm, users, groups,
          vuRoutes.createDocPost(id)))
      }
      case Right(doc) => immediate(Redirect(vuRoutes.get(doc.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", doc.id)))
    }
  }

  def delete(id: String) = deleteAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, vuRoutes.deletePost(id),
        vuRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(vuRoutes.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, vuRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(vuRoutes.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        vuRoutes.addItemPermissions(id),
        vuRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        vuRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        vuRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        vuRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        vuRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(vuRoutes.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def linkTo(id: String) = withItemPermission[VirtualUnit](id, PermissionType.Annotate, contentType) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.virtualUnit.linkTo(item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = linkSelectAction(id, toType) {
    item => page => params => facets => etype => implicit userOpt => implicit request =>
      Ok(views.html.link.linkSourceList(item, page, params, facets, etype,
          vuRoutes.linkAnnotateSelect(id, toType),
          vuRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.link.link(target, source,
        Link.form, vuRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
        BadRequest(views.html.link.link(target, source,
          errorForm, vuRoutes.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(vuRoutes.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }

  def linkMultiAnnotate(id: String) = linkMultiAction(id) {
      target => implicit userOpt => implicit request =>
    Ok(views.html.link.linkMulti(target,
        Link.multiForm, vuRoutes.linkMultiAnnotatePost(id)))
  }

  def linkMultiAnnotatePost(id: String) = linkPostMultiAction(id) {
      formOrAnnotations => implicit userOpt => implicit request =>
    formOrAnnotations match {
      case Left((target,errorForms)) => {
        BadRequest(views.html.link.linkMulti(target,
          errorForms, vuRoutes.linkMultiAnnotatePost(id)))
      }
      case Right(annotations) => {
        Redirect(vuRoutes.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}


