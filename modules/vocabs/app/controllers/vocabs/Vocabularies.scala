package controllers.vocabs

import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import controllers.generic._
import models._
import defines.{ContentTypes, EntityType}
import utils.search.{Indexer, Resolver, Dispatcher}
import com.google.inject._
import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import solr.SolrConstants


@Singleton
case class Vocabularies @Inject()(implicit globalConfig: global.GlobalConfig, searchDispatcher: Dispatcher, searchIndexer: Indexer, searchResolver: Resolver, backend: Backend, userDAO: AccountDAO) extends CRUD[VocabularyF,Vocabulary]
  with Creator[ConceptF, Concept, Vocabulary]
  with Visibility[Vocabulary]
  with ScopePermissions[Vocabulary]
  with Annotate[Vocabulary]
  with Indexable[Vocabulary]
  with Search {

  implicit val resource = Vocabulary.Resource

  val contentType = ContentTypes.Vocabulary
  val targetContentTypes = Seq(ContentTypes.Concept)

  val form = models.Vocabulary.form
  val childForm = models.Concept.form

  private val vocabRoutes = controllers.vocabs.routes.Vocabularies

  def get(id: String) = getAction.async(id) { item => annotations => links => implicit userOpt => implicit request =>
    find[Concept](
      filters = Map(SolrConstants.HOLDER_ID -> item.id),
      entities = List(EntityType.Concept)
    ).map { result =>
      Ok(views.html.vocabulary.show(
          item, result.page, result.params, result.facets,
        vocabRoutes.get(id), annotations, links))
    }
  }

  def history(id: String) = historyAction(id) { item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def list = pageAction { page => params => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.list(page, params))
  }

  def create = createAction { users => groups => implicit userOpt => implicit request =>
    Ok(views.html.vocabulary.create(form, VisibilityForm.form, users, groups, vocabRoutes.createPost()))
  }

  def createPost = createPostAction.async(form) { formsOrItem => implicit userOpt => implicit request =>
    formsOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.vocabulary.create(errorForm, accForm, users, groups, vocabRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
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
        .flashing("success" -> "item.update.confirmation")
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
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
        item, vocabRoutes.deletePost(id),
        vocabRoutes.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.list())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, vocabRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = manageScopedPermissionsAction(id) {
      item => perms => sperms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.manageScopedPermissions(item, perms, sperms,
        vocabRoutes.addItemPermissions(id), vocabRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionItem(item, users, groups,
        vocabRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = addItemPermissionsAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.permissionScope(item, users, groups,
        vocabRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionItem(item, accessor, perms, contentType,
        vocabRoutes.setItemPermissionsPost(id, userType, userId)))
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setItemPermissionsPostAction(id, userType, userId) {
      bool => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsAction(id, userType, userId) {
      item => accessor => perms => implicit userOpt => implicit request =>
    Ok(views.html.permissions.setPermissionScope(item, accessor, perms, targetContentTypes,
        vocabRoutes.setScopedPermissionsPost(id, userType, userId)))
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = setScopedPermissionsPostAction(id, userType, userId) {
      perms => implicit userOpt => implicit request =>
    Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def updateIndex(id: String) = adminAction.async { implicit userOpt => implicit request =>
    getEntity(id, userOpt) { item =>
      Ok(views.html.search.updateItemIndex(item,
        action = vocabRoutes.updateIndexPost(id)))
    }
  }

  def updateIndexPost(id: String) = updateChildItemsPost(SolrConstants.HOLDER_ID, id)
}


