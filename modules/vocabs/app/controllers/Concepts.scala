package controllers.vocabs

import _root_.controllers.ListParams
import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import controllers.base._
import models._
import models.forms.LinkForm
import play.api._
import play.api.i18n.Messages
import defines.{ContentType, EntityType}
import collection.immutable.ListMap
import solr.facet.FieldFacetClass
import views.Helpers
import utils.search.{SearchParams, FacetSort}

import utils.search.Dispatcher
import com.google.inject._

@Singleton
class Concepts @Inject()(implicit val globalConfig: global.GlobalConfig) extends CreationContext[ConceptF, Concept, Concept]
  with VisibilityController[Concept]
  with EntityRead[Concept]
  with EntityUpdate[ConceptF, Concept]
  with EntityDelete[Concept]
  with PermissionScopeController[Concept]
  with EntityLink[Concept]
  with EntityAnnotate[Concept]
  with EntitySearch
  with ApiBase[Concept] {

  val targetContentTypes = Seq(ContentType.Concept)

  val entityType = EntityType.Concept
  val contentType = ContentType.Concept

  val form = models.forms.ConceptForm.form
  val childForm = models.forms.ConceptForm.form


  override val entityFacets = List(
    FieldFacetClass(
      key="languageCode", // FIXME - define elsewhere
      name=Messages("concept.languageCode"),
      param="lang",
      render=Helpers.languageCodeToName
    ),
    FieldFacetClass(
      key="holderName",
      name=Messages("concept.inVocabulary"),
      param="set",
      sort = FacetSort.Name
    )
  )

  // Search params
  val DEFAULT_SEARCH_PARAMS = SearchParams(entities = List(entityType))


  def get(id: String) = getWithChildrenAction[Concept](id) { item => page => params => annotations => links =>
      implicit userOpt => implicit request =>
    Ok(views.html.concept.show(item, page, params, annotations))
  }

  def search = {
    searchAction[Concept](defaultParams = Some(DEFAULT_SEARCH_PARAMS)) {
        page => params => facets => implicit userOpt => implicit request =>
      Ok(views.html.concept.search(page, params, facets, controllers.vocabs.routes.Concepts.search))
    }
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.concept.list(page, params))
  }

  def update(id: String) = updateAction(id) {
      item => implicit userOpt => implicit request =>
    Ok(views.html.concept.edit(item, form.fill(item.model),controllers.vocabs.routes.Concepts.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      oldItem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.concept.edit(
          oldItem, errorForm, controllers.vocabs.routes.Concepts.updatePost(id)))
      case Right(item) => Redirect(controllers.vocabs.routes.Concepts.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createConcept(id: String) = childCreateAction(id, ContentType.Concept) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.concept.create(
        item, childForm, VisibilityForm.form, users, groups, controllers.vocabs.routes.Concepts.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction(id, childForm, ContentType.Concept) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.concept.create(item,
          errorForm, accForm, users, groups, controllers.vocabs.routes.Concepts.createConceptPost(id)))
      }
      case Right(citem) => Redirect(controllers.vocabs.routes.Concepts.get(id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.vocabs.routes.Concepts.deletePost(id), controllers.vocabs.routes.Concepts.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Concepts.search())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.vocabs.routes.Concepts.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Concepts.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.vocabs.routes.Concepts.addItemPermissions(id), controllers.vocabs.routes.Concepts.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.vocabs.routes.Concepts.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.vocabs.routes.Concepts.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.vocabs.routes.Concepts.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Concepts.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.vocabs.routes.Concepts.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Concepts.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def linkAnnotate(id: String, toType: String, to: String) = linkAction(id, toType, to) {
      target => source => implicit userOpt => implicit request =>
    Ok(views.html.link.link(target, source,
            LinkForm.form, controllers.vocabs.routes.Concepts.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: String, to: String) = linkPostAction(id, toType, to) {
      formOrAnnotation => implicit userOpt => implicit request =>
    formOrAnnotation match {
      case Left((target,source,errorForm)) => {
          BadRequest(views.html.link.link(target, source,
              errorForm, controllers.vocabs.routes.Concepts.linkAnnotatePost(id, toType, to)))
      }
      case Right(annotation) => {
        Redirect(controllers.vocabs.routes.Concepts.get(id))
          .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
      }
    }
  }
}


