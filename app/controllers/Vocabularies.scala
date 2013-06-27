package controllers

import play.api.libs.concurrent.Execution.Implicits._
import models._
import models.forms.VisibilityForm
import play.api._
import play.api.i18n.Messages
import _root_.controllers.base._
import defines.{ContentType, EntityType}
import solr.SearchParams
import scala.Some

object Vocabularies extends CRUD[VocabularyF,VocabularyMeta]
  with CreationContext[ConceptF, ConceptMeta, VocabularyMeta]
  with VisibilityController[VocabularyMeta]
  with PermissionScopeController[VocabularyMeta]
  with EntityAnnotate[VocabularyMeta]
  with EntitySearch {

  val targetContentTypes = Seq(ContentType.Concept)

  override def processParams(params: ListParams): rest.RestPageParams = {
    params.toRestParams(Concepts.listFilterMappings, Concepts.orderMappings, Some(Concepts.DEFAULT_SORT))
  }
  override def processChildParams(params: ListParams) = Concepts.processParams(params)


  val entityType = EntityType.Vocabulary
  val contentType = ContentType.Vocabulary

  val form = models.forms.VocabularyForm.form
  val childForm = models.forms.ConceptForm.form

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[ConceptMeta](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.Concept)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.vocabulary.show(
          item, page, params, facets, routes.Vocabularies.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.create(form, VisibilityForm.form, users, groups, routes.Vocabularies.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.vocabulary.create(errorForm, accForm, users, groups, routes.Vocabularies.createPost))
      }
      case Right(item) => Redirect(routes.Vocabularies.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.edit(
      item, form.fill(item.model),routes.Vocabularies.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.vocabulary.edit(
          olditem, errorForm, routes.Vocabularies.updatePost(id)))
      case Right(item) => Redirect(routes.Vocabularies.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createConcept(id: String) = childCreateAction(id, ContentType.Concept) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.concept.create(
      item, childForm, VisibilityForm.form, users, groups, routes.Vocabularies.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction(id, childForm, ContentType.Concept) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.concept.create(item,
          errorForm, accForm, users, groups, routes.Vocabularies.createConceptPost(id)))
      }
      case Right(citem) => Redirect(routes.Vocabularies.get(id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, routes.Vocabularies.deletePost(id),
        routes.Vocabularies.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Vocabularies.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        models.forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, routes.Vocabularies.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Vocabularies.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        routes.Vocabularies.addItemPermissions(id), routes.Vocabularies.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        routes.Vocabularies.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        routes.Vocabularies.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        routes.Vocabularies.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        routes.Vocabularies.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


