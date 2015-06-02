package controllers.vocabularies

import auth.AccountManager
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import forms.VisibilityForm
import controllers.generic._
import models._
import defines.{ContentTypes, EntityType}
import utils.search.{SearchConstants, SearchIndexer, SearchItemResolver, SearchEngine}
import javax.inject._
import views.MarkdownRenderer

import scala.concurrent.Future.{successful => immediate}
import backend.Backend
import controllers.base.AdminController


@Singleton
case class Vocabularies @Inject()(implicit app: play.api.Application, cache: CacheApi, globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchIndexer: SearchIndexer, searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager, pageRelocator: utils.MovedPageLookup, messagesApi: MessagesApi, markdown: MarkdownRenderer)
  extends AdminController
  with CRUD[VocabularyF,Vocabulary]
  with Creator[ConceptF, Concept, Vocabulary]
  with Visibility[Vocabulary]
  with ScopePermissions[Vocabulary]
  with Annotate[Vocabulary]
  with Indexable[Vocabulary]
  with Search {

  val targetContentTypes = Seq(ContentTypes.Concept)

  val form = models.Vocabulary.form
  val childForm = models.Concept.form

  private val vocabRoutes = controllers.vocabularies.routes.Vocabularies

  def get(id: String) = ItemMetaAction(id).async { implicit request =>
    findType[Concept](
      filters = Map(SearchConstants.HOLDER_ID -> request.item.id)
    ).map { result =>
      Ok(views.html.admin.vocabulary.show(
        request.item, result,
        vocabRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.vocabulary.list(request.page, request.params))
  }

  def create = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.vocabulary.create(form, VisibilityForm.form,
      request.users, request.groups, vocabRoutes.createPost()))
  }

  def createPost = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.vocabulary.create(errorForm, accForm,
          users, groups, vocabRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.vocabulary.edit(
      request.item, form.fill(request.item.model),vocabRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.vocabulary.edit(
          request.item, errorForm, vocabRoutes.updatePost(id)))
      case Right(item) => Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createConcept(id: String) = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.concept.create(
      request.item, childForm, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, vocabRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String) = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm,accForm)) => getUsersAndGroups { users => groups =>
        BadRequest(views.html.admin.concept.create(request.item,
          errorForm, accForm, users, groups, vocabRoutes.createConceptPost(id)))
      }
      case Right(_) => immediate(Redirect(vocabRoutes.get(id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
        request.item, vocabRoutes.deletePost(id), vocabRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(vocabRoutes.list())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups, vocabRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(vocabRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = ScopePermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.manageScopedPermissions(
      request.item, request.permissionGrants, request.scopePermissionGrants,
        vocabRoutes.addItemPermissions(id), vocabRoutes.addScopedPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        vocabRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
        vocabRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        vocabRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        vocabRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def updateIndex(id: String) = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
      Ok(views.html.admin.search.updateItemIndex(request.item,
          action = vocabRoutes.updateIndexPost(id)))
  }

  def updateIndexPost(id: String) = updateChildItemsPost(SearchConstants.HOLDER_ID, id)
}


