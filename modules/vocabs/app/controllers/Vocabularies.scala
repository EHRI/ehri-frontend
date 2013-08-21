package controllers.vocabs

import _root_.controllers.ListParams
import forms.VisibilityForm
import play.api.libs.concurrent.Execution.Implicits._
import models._
import play.api._
import play.api.i18n.Messages
import _root_.controllers.base._
import defines.{ContentType, EntityType}
import scala.Some
import utils.search.SearchParams
import utils.search.Dispatcher
import com.google.inject._

@Singleton
class Vocabularies @Inject()(implicit val globalConfig: global.GlobalConfig) extends CRUD[VocabularyF,Vocabulary]
  with CreationContext[ConceptF, Concept, Vocabulary]
  with VisibilityController[Vocabulary]
  with PermissionScopeController[Vocabulary]
  with EntityAnnotate[Vocabulary]
  with EntitySearch {

  val targetContentTypes = Seq(ContentType.Concept)

  val entityType = EntityType.Vocabulary
  val contentType = ContentType.Vocabulary

  val form = models.forms.VocabularyForm.form
  val childForm = models.forms.ConceptForm.form

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[Concept](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.Concept)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.vocabulary.show(
          item, page, params, facets, controllers.vocabs.routes.Vocabularies.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = listAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.create(form, VisibilityForm.form, users, groups, controllers.vocabs.routes.Vocabularies.createPost))
  }

  def createPost = createPostAction(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.vocabulary.create(errorForm, accForm, users, groups, controllers.vocabs.routes.Vocabularies.createPost))
      }
      case Right(item) => Redirect(controllers.vocabs.routes.Vocabularies.get(item.id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", item.id))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.edit(
      item, form.fill(item.model),controllers.vocabs.routes.Vocabularies.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.vocabulary.edit(
          olditem, errorForm, controllers.vocabs.routes.Vocabularies.updatePost(id)))
      case Right(item) => Redirect(controllers.vocabs.routes.Vocabularies.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("confirmations.itemWasUpdated", item.id))
    }
  }

  def createConcept(id: String) = childCreateAction(id, ContentType.Concept) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.concept.create(
      item, childForm, VisibilityForm.form, users, groups, controllers.vocabs.routes.Vocabularies.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction(id, childForm, ContentType.Concept) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.concept.create(item,
          errorForm, accForm, users, groups, controllers.vocabs.routes.Vocabularies.createConceptPost(id)))
      }
      case Right(citem) => Redirect(controllers.vocabs.routes.Vocabularies.get(id))
        .flashing("success" -> Messages("confirmations.itemWasCreated", citem.id))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, controllers.vocabs.routes.Vocabularies.deletePost(id),
        controllers.vocabs.routes.Vocabularies.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Vocabularies.list())
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.vocabs.routes.Vocabularies.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Vocabularies.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def managePermissions(id: String, page: Int = 1, spage: Int = 1, limit: Int = DEFAULT_LIMIT) =
    manageScopedPermissionsAction(id, page, spage, limit) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        controllers.vocabs.routes.Vocabularies.addItemPermissions(id), controllers.vocabs.routes.Vocabularies.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        controllers.vocabs.routes.Vocabularies.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        controllers.vocabs.routes.Vocabularies.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: String, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        controllers.vocabs.routes.Vocabularies.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: String, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def setScopedPermissions(id: String, userType: String, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        controllers.vocabs.routes.Vocabularies.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: String, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(controllers.vocabs.routes.Vocabularies.managePermissions(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }
}


