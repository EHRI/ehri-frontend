package controllers.keywords

import forms.VisibilityForm
import controllers.generic._
import models.{Link, AccountDAO, Concept, ConceptF}
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import views.Helpers
import utils.search.{FacetDisplay, Resolver, FacetSort, Dispatcher}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import solr.facet.FieldFacetClass
import backend.Backend
import models.base.Description


@Singleton
case class Concepts @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends Creator[ConceptF, Concept, Concept]
  with Visibility[Concept]
  with Read[Concept]
  with Update[ConceptF, Concept]
  with Delete[Concept]
  with ScopePermissions[Concept]
  with Linking[Concept]
  with Annotate[Concept]
  with Search {

  val targetContentTypes = Seq(ContentTypes.Concept)

  private val form = models.Concept.form
  private val childForm = models.Concept.form
  private val conceptRoutes = controllers.keywords.routes.Concepts

  private def entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key=Description.LANG_CODE,
        name=Messages("cvocConcept." + Description.LANG_CODE),
        param="lang",
        render=(s: String) => Helpers.languageCodeToName(s),
        display = FacetDisplay.DropDown,
        sort = FacetSort.Name
      ),
      FieldFacetClass(
        key="holderName",
        name=Messages("cvocConcept.inVocabulary"),
        param="set",
        sort = FacetSort.Name
      )
    )
  }


  def get(id: String) = getWithChildrenAction[Concept](id) {
      item => page => params => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.admin.concept.show(item, page, params, links, annotations))
  }

  def search = searchAction[Concept](entities = List(EntityType.Concept), entityFacets = entityFacets) {
      page => params => facets => implicit userOpt => implicit request =>
    Ok(views.html.admin.concept.search(page, params, facets, conceptRoutes.search()))
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.concept.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.admin.concept.edit(item, form.fill(item.model),conceptRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      oldItem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.concept.edit(
          oldItem, errorForm, conceptRoutes.updatePost(id)))
      case Right(item) => Redirect(conceptRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createConcept(id: String) = childCreateAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.concept.create(
        item, childForm, VisibilityForm.form, users, groups, conceptRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction.async(id, childForm) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.concept.create(item,
          errorForm, accForm, users, groups, conceptRoutes.createConceptPost(id)))
      }
      case Right(citem) => immediate(Redirect(conceptRoutes.get(id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.delete(
        item, conceptRoutes.deletePost(id), conceptRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { implicit userOpt => implicit request =>
    Redirect(conceptRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, conceptRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(conceptRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(item, perms, sperms,
        conceptRoutes.addItemPermissions(id), conceptRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionItem(item, users, groups,
        conceptRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.permissionScope(item, users, groups,
        conceptRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionItem(item, accessor, perms, Concept.Resource.contentType,
        conceptRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(conceptRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        conceptRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(conceptRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.admin.link.create(target, source,
            Link.form, conceptRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
          BadRequest(views.html.admin.link.create(target, source,
              errorForm, conceptRoutes.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(conceptRoutes.get(id))
          .flashing("success" -> "item.update.confirmation")
      }
    }
  }
}


