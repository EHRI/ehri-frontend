package controllers.authorities

import javax.inject._
import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import defines.{EntityType, PermissionType}
import forms.VisibilityForm
import models._
import forms.FormConfigBuilder
import play.api.i18n.Messages
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import services.search._
import utils.{PageParams, RangeParams}


@Singleton
case class HistoricalAgents @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with CRUD[HistoricalAgent]
  with Visibility[HistoricalAgent]
  with ItemPermissions[HistoricalAgent]
  with Linking[HistoricalAgent]
  with Annotate[HistoricalAgent]
  with SearchType[HistoricalAgent]
  with Descriptions[HistoricalAgent]
  with AccessPoints[HistoricalAgent] {

  private val form = models.HistoricalAgent.form
  private val histRoutes = controllers.authorities.routes.HistoricalAgents

  // Documentary unit facets
  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = models.Isaar.ENTITY_TYPE,
        name = Messages("historicalAgent." + Isaar.ENTITY_TYPE),
        param = "cpf",
        render = s => Messages("historicalAgent." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = SearchConstants.HOLDER_NAME,
        name = Messages("historicalAgent.authoritativeSet"),
        param = "set",
        sort = FacetSort.Name
      )
    )
  }

  private val formConfig: FormConfigBuilder = FormConfigBuilder(EntityType.HistoricalAgent, config)

  def search(params: SearchParams, paging: PageParams): Action[AnyContent] =
    SearchTypeAction(params, paging, facetBuilder = entityFacets).apply { implicit request =>
      Ok(views.html.admin.historicalAgent.search(request.result, histRoutes.search()))
    }

  def get(id: String): Action[AnyContent] = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.show(request.item, request.annotations, request.links))
      .withPreferences(preferences.withRecentItem(id))
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def list(paging: PageParams): Action[AnyContent] = ItemPageAction(paging).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.list(request.page, request.params))
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.historicalAgent.edit(
      request.item, form.fill(request.item.data), formConfig.forUpdate, histRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) =>
        BadRequest(views.html.admin.historicalAgent
          .edit(request.item, errorForm, formConfig.forUpdate, histRoutes.updatePost(id)))
      case Right(updated) => Redirect(histRoutes.get(updated.id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(request.item, histRoutes.deletePost(id),
      histRoutes.get(id)))
  }

  def deletePost(id: String): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(histRoutes.search())
      .flashing("success" -> "item.delete.confirmation")
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, histRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(histRoutes.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def managePermissions(id: String, paging: PageParams): Action[AnyContent] =
    PermissionGrantAction(id, paging).apply { implicit request =>
      Ok(views.html.admin.permissions.managePermissions(request.item, request.permissionGrants,
        histRoutes.addItemPermissions(id)))
    }

  def addItemPermissions(id: String): Action[AnyContent] = EditItemPermissionsAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.permissionItem(request.item, request.usersAndGroups,
      histRoutes.setItemPermissions))
  }

  def setItemPermissions(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    CheckUpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Ok(views.html.admin.permissions.setPermissionItem(
        request.item, request.accessor, request.itemPermissions,
        histRoutes.setItemPermissionsPost(id, userType, userId)))
    }
  }

  def setItemPermissionsPost(id: String, userType: EntityType.Value, userId: String): Action[AnyContent] = {
    UpdateItemPermissionsAction(id, userType, userId).apply { implicit request =>
      Redirect(histRoutes.managePermissions(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def linkTo(id: String): Action[AnyContent] =
    WithItemPermissionAction(id, PermissionType.Annotate).apply { implicit request =>
      Ok(views.html.admin.historicalAgent.linkTo(request.item))
    }

  def linkAnnotateSelect(id: String, toType: EntityType.Value, params: SearchParams, paging: PageParams): Action[AnyContent] =
    LinkSelectAction(id, toType, params, paging, facets = entityFacets).apply { implicit request =>
      Ok(views.html.admin.link.linkSourceList(
        request.item, request.searchResult, request.entityType,
        histRoutes.linkAnnotateSelect(id, toType),
        (other, _) => histRoutes.linkAnnotate(id, toType, other)))
    }

  def linkAnnotate(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    LinkAction(id, toType, to).apply { implicit request =>
      Ok(views.html.admin.link.create(request.from, request.to,
        Link.form, histRoutes.linkAnnotatePost(id, toType, to)))
    }

  def linkAnnotatePost(id: String, toType: EntityType.Value, to: String): Action[AnyContent] =
    CreateLinkAction(id, toType, to).apply { implicit request =>
      request.formOrLink match {
        case Left((target, errorForm)) =>
          BadRequest(views.html.admin.link.create(request.from, target,
            errorForm, histRoutes.linkAnnotatePost(id, toType, to)))
        case Right(_) =>
          Redirect(histRoutes.get(id))
            .flashing("success" -> "item.update.confirmation")
      }
    }

  def manageAccessPoints(id: String, descriptionId: String): Action[AnyContent] =
    WithDescriptionAction(id, descriptionId).apply { implicit request =>
      val holders = config.getOptional[Seq[String]]("ehri.admin.accessPoints.holders")
        .getOrElse(Seq.empty)
      Ok(views.html.admin.historicalAgent.editAccessPoints(request.item,
        request.description, holderIds = holders))
    }
}
