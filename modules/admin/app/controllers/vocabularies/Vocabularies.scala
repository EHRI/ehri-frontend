package controllers.vocabularies

import javax.inject._

import services.rest.DataHelpers
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{ContentTypes, EntityType}
import forms.VisibilityForm
import models._
import play.api.data.Form
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import utils.{PageParams, RangeParams}
import utils.search.{SearchConstants, SearchIndexMediator, SearchParams}

import scala.concurrent.Future.{successful => immediate}


@Singleton
case class Vocabularies @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers,
  searchIndexer: SearchIndexMediator,
  ws: WSClient
) extends AdminController
  with CRUD[VocabularyF, Vocabulary]
  with Creator[ConceptF, Concept, Vocabulary]
  with Visibility[Vocabulary]
  with ScopePermissions[Vocabulary]
  with Annotate[Vocabulary]
  with Search {

  override protected val targetContentTypes = Seq(ContentTypes.Concept)
  private val form: Form[VocabularyF] = models.Vocabulary.form
  private val childForm: Form[ConceptF] = models.Concept.form

  private val vocabRoutes = controllers.vocabularies.routes.Vocabularies

  def get(id: String, params: SearchParams, paging: PageParams): Action[AnyContent] = ItemMetaAction(id).async { implicit request =>
    findType[Concept](params, paging, filters = Map(SearchConstants.HOLDER_ID -> request.item.id)).map { result =>
      Ok(views.html.admin.vocabulary.show(
        request.item, result,
        vocabRoutes.get(id), request.annotations, request.links))
    }
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.vocabulary.list(request.page, request.params))
  }

  def create: Action[AnyContent] = NewItemAction.apply { implicit request =>
    Ok(views.html.admin.vocabulary.create(form, VisibilityForm.form,
      request.users, request.groups, vocabRoutes.createPost()))
  }

  def createPost: Action[AnyContent] = CreateItemAction(form).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.vocabulary.create(errorForm, accForm,
          users, groups, vocabRoutes.createPost()))
      }
      case Right(item) => immediate(Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.vocabulary.edit(
      request.item, form.fill(request.item.model), vocabRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.vocabulary.edit(
        request.item, errorForm, vocabRoutes.updatePost(id)))
      case Right(item) => Redirect(vocabRoutes.get(item.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def createConcept(id: String): Action[AnyContent] = NewChildAction(id).apply { implicit request =>
    Ok(views.html.admin.concept.create(
      request.item, childForm, VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, vocabRoutes.createConceptPost(id)))
  }

  def createConceptPost(id: String): Action[AnyContent] = CreateChildAction(id, childForm).async { implicit request =>
    request.formOrItem match {
      case Left((errorForm, accForm)) => dataHelpers.getUserAndGroupList.map { case (users, groups) =>
        BadRequest(views.html.admin.concept.create(request.item,
          errorForm, accForm, users, groups, vocabRoutes.createConceptPost(id)))
      }
      case Right(_) => immediate(Redirect(vocabRoutes.get(id))
        .flashing("success" -> "item.create.confirmation"))
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, vocabRoutes.deletePost(id), vocabRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(vocabRoutes.list())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, vocabRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(vocabRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams, scopePaging: PageParams): Action[AnyContent] =
    ScopePermissionGrantAction(id, paging, scopePaging).apply { implicit request =>
      Ok(views.html.admin.permissions.manageScopedPermissions(
        request.item, request.permissionGrants, request.scopePermissionGrants,
        vocabRoutes.addItemPermissions(id), vocabRoutes.addScopedPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
      vocabRoutes.setItemPermissions))
  }

  def addScopedPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionScope(request.item, request.users, request.groups,
      vocabRoutes.setScopedPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        vocabRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def setScopedPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionScope(
        request.item, request.accessor, request.scopePermissions, targetContentTypes,
        vocabRoutes.setScopedPermissionsPost(id, userType, userId)))
    }
  }

  def setScopedPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateScopePermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(vocabRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def updateIndex(id: String): Action[AnyContent] = (AdminAction andThen ItemPermissionAction(id)).apply { implicit request =>
      Ok(views.html.admin.search.updateItemIndex(request.item, field = SearchConstants.HOLDER_ID,
          action = controllers.admin.routes.Indexing.indexer()))
  }

  def exportSkos(id: String, format: Option[String], baseUri: Option[String]): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    val params: Map[String, Seq[String]] = (format.toSeq.map(f => "format" -> Seq(f)) ++
      baseUri.toSeq.map(url => "baseUri" -> Seq(url))).toMap
    userDataApi.query(s"classes/${EntityType.Vocabulary}/$id/export", params = params).map { sr =>
      val ct = sr.headers.get(HeaderNames.CONTENT_TYPE)
        .flatMap(_.headOption)
        .getOrElse(MimeTypes.TEXT)
      Ok.chunked(sr.bodyAsSource).as(ct)
    }
  }
}


