package controllers.authorities

import auth.AccountManager
import controllers.generic._
import forms.VisibilityForm
import models._
import play.api.i18n.Messages
import defines.{EntityType, PermissionType}
import utils.search._
import com.google.inject._
import backend.Backend
import controllers.base.AdminController


@Singleton
case class HistoricalAgents @Inject()(implicit globalConfig: global.GlobalConfig, searchEngine: SearchEngine, searchResolver: SearchItemResolver, backend: Backend, accounts: AccountManager)
  extends AdminController with CRUD[HistoricalAgentF,HistoricalAgent]
	with Visibility[HistoricalAgent]
  with ItemPermissions[HistoricalAgent]
  with Linking[HistoricalAgent]
  with Annotate[HistoricalAgent]
  with SearchType[HistoricalAgent] {

  private val form = models.HistoricalAgent.form
  private val histRoutes = controllers.authorities.routes.HistoricalAgents

  // Documentary unit facets
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key=models.Isaar.ENTITY_TYPE,
        name=Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param="cpf",
        render=s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key=SearchConstants.HOLDER_NAME,
        name=Messages("historicalAgent.authoritativeSet"),
        param="set",
        sort = FacetSort.Name
      )
    )
  }


  def search = SearchTypeAction(facetBuilder = entityFacets).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.search(request.result, histRoutes.search()))
  }

  def get(id: String) = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.show(request.item, request.annotations, request.links))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def list = ItemPageAction.apply { implicit request =>
    Ok(views.html.admin.historicalAgent.list(request.page, request.params))
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.edit(
      request.item, form.fill(request.item.model), histRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.historicalAgent
          .edit(request.item, errorForm, histRoutes.updatePost(id)))
      case Right(updated) => Redirect(histRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(request.item, histRoutes.deletePost(id),
        histRoutes.get(id)))
  }

  def deletePost(id: String) = DeleteAction(id).apply { implicit request =>
    Redirect(histRoutes.search())
        .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, histRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(histRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String) = PermissionGrantAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
        histRoutes.addItemPermissions(id)))
  }

  def addItemPermissions(id: String) = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.users, request.groups,
        histRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String) = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions, HistoricalAgent.Resource.contentType,
        histRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String) = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(histRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String) = WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.linkTo(request.item))
  }

  def linkAnnotateSelect(id: String, toType: EntityType.Value) = LinkSelectAction(id, toType, facets = entityFacets).apply { implicit request =>
    Ok(views.html.admin.link.linkSourceList(
      request.item, request.searchResult, request.entityType,
        histRoutes.linkAnnotateSelect(id, toType),
        histRoutes.linkAnnotate))
  }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String) = LinkAction(id, toType, to).apply { implicit request =>
    Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, histRoutes.linkAnnotatePost(id, toType, to)))
  }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String) = CreateLinkAction(id, toType, to).apply { implicit request =>
      request.formOrLink match {
        case Left((target,errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, histRoutes.linkAnnotatePost(id, toType, to)))
        case Right(_) =>
          Redirect(histRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
  }
}