package controllers.links

import javax.inject._

import backend.rest.DataHelpers
import controllers.Components
import controllers.base.AdminController
import controllers.generic._
import defines.EntityType
import forms.VisibilityForm
import models.{Link, LinkF}
import play.api.mvc.{Action, AnyContent, Call}
import play.api.mvc.Call
import play.api.i18n.Messages
import utils.search._


case class Links @Inject()(
  components: Components,
  dataHelpers: DataHelpers
) extends AdminController
  with Read[Link]
  with Visibility[Link]
  with Promotion[Link]
  with Update[LinkF, Link]
  with Delete[Link]
  with Annotate[Link]
  with Search {

  private val form = Link.form
  private val linkRoutes = controllers.links.routes.Links

  private val entityFacets: FacetBuilder = { implicit request =>
    List(
      FieldFacetClass(
        key = "linkType",
        name = Messages("link.type"),
        param = "linkType",
        render = s => Messages("link." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = "linkField",
        name = Messages("link.field"),
        param = "linkField",
        render = s => Messages("link." + s),
        display = FacetDisplay.Choice
      ),
      FieldFacetClass(
        key = "targetTypes",
        name = Messages("link.targetType"),
        param = "targetType",
        render = s => Messages("contentTypes." + s),
        display = FacetDisplay.Choice
      )
    )
  }

  def search(): Action[AnyContent] = OptionalUserAction.async { implicit request =>
    // We only care here about links which:
    // - don't have a body, i.e. are not connected to access points
    // - connect only 2 items
    // - one of which is a doc unit
    val targetTypes = Seq(
      EntityType.DocumentaryUnit,
      EntityType.Repository,
      EntityType.HistoricalAgent
    )
    val filters = Map(
      "hasBody" -> false,
      "targetCount" -> 2,
      s"targetTypes:(${targetTypes.mkString(" ")})" -> Unit
    )

    findType[Link](
      filters = filters,
      facetBuilder = entityFacets
    ).map { result =>
      Ok(views.html.admin.link.search(result, linkRoutes.search()))
    }
  }

  private def redirectLink(src: Option[String], alt: Call): Call =
    src.map(r => controllers.admin.routes.Data.getItem(r)).getOrElse(alt)

  def get(id: String): Action[AnyContent] = getAndRedirect(id, None)

  def getAndRedirect(id: String, redirect: Option[String] = None): Action[AnyContent] = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.link.show(request.item, request.annotations, redirect))
  }

  def history(id: String): Action[AnyContent] = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups,  linkRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }
  def update(id: String, redirect: Option[String] = None): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.link.edit(
      request.item, form.fill(request.item.model), linkRoutes.updatePost(id, redirect)))
  }

  def updatePost(id: String, redirect: Option[String] = None): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.link.edit(
          request.item, errorForm, linkRoutes.updatePost(id, redirect)))
      case Right(item) => Redirect(redirectLink(redirect, linkRoutes.get(id)))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String, redirect: Option[String] = None): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, linkRoutes.deletePost(id, redirect),
        controllers.admin.routes.Data.getItem(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(redirectLink(redirect, controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.promote(request.item, linkRoutes.promotePost(id)))
  }

  def promotePost(id: String): Action[AnyContent] = PromoteItemAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.demote(request.item,
      linkRoutes.demotePost(id)))
  }

  def demotePost(id: String): Action[AnyContent] = DemoteItemAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}