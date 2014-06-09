package controllers.vocabs

import _root_.forms.VisibilityForm
import controllers.generic._
import models._
import play.api.i18n.Messages
import defines.{ContentTypes, EntityType}
import utils.search.{Resolver, Dispatcher, SearchParams}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.Backend


@Singleton
case class Vocabularies @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends CRUD[VocabularyF,Vocabulary]
  with Creator[ConceptF, Concept, Vocabulary]
  with Visibility[Vocabulary]
  with ScopePermissions[Vocabulary]
  with Annotate[Vocabulary]
  with Search {

  implicit val resource = Vocabulary.Resource

  val contentType = ContentTypes.Vocabulary
  val targetContentTypes = Seq(ContentTypes.Concept)

  val form = models.Vocabulary.form
  val childForm = models.Concept.form

  private val vocabRoutes = controllers.vocabs.routes.Vocabularies

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    searchAction[Concept](Map("holderId" -> item.id), defaultParams = Some(SearchParams(entities=List(EntityType.Concept)))) {
      page => params => facets => _ => _ =>
        Ok(views.html.vocabulary.show(
          item, page, params, facets, vocabRoutes.get(id), annotations, links))
    }.apply(request)
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.create(form, VisibilityForm.form, users, groups, vocabRoutes.createPost))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.vocabulary.create(errorForm, accForm, users, groups, vocabRoutes.createPost))
      }
      case Right(item) => immediate(Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> Messages("item.create.confirmation", item.id)))
    }
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.edit(
      item, form.fill(item.model),vocabRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) {
      olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.vocabulary.edit(
          olditem, errorForm, vocabRoutes.updatePost(id)))
      case Right(item) => Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> play.api.i18n.Messages("item.update.confirmation", item.id))
    }
  }

  def createConcept(id: String) = childCreateAction(id, ContentTypes.Concept) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.concept.create(
      item, childForm, VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, vocabRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String) = childCreatePostAction.async(id, childForm, ContentTypes.Concept) {
      item => formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.concept.create(item,
          errorForm, accForm, users, groups, vocabRoutes.createConceptPost(id)))
      }
      case Right(citem) => immediate(Redirect(vocabRoutes.get(id))
        .flashing("success" -> Messages("item.create.confirmation", citem.id)))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, vocabRoutes.deletePost(id),
        vocabRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.list())
        .flashing("success" -> Messages("item.delete.confirmation", id))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, vocabRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.get(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        vocabRoutes.addItemPermissions(id), vocabRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        vocabRoutes.setItemPermissions _))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        vocabRoutes.setScopedPermissions _))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        vocabRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        vocabRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> Messages("item.update.confirmation", id))
  }
}


