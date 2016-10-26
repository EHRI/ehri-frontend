package controllers.linking

import auth.AccountManager
import forms.VisibilityForm
import models.{Link, LinkF}
import javax.inject._

import auth.handler.AuthHandler
import controllers.generic._
import backend.DataApi
import backend.rest.DataHelpers
import controllers.base.AdminController
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import play.api.mvc.Call
import utils.MovedPageLookup
import views.MarkdownRenderer

import scala.concurrent.ExecutionContext


case class Links @Inject()(
  implicit config: play.api.Configuration,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  authHandler: AuthHandler,
  executionContext: ExecutionContext,
  dataApi: DataApi,
  dataHelpers: DataHelpers,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer
) extends AdminController
  with Read[Link]
  with Visibility[Link]
  with Promotion[Link]
  with Update[LinkF, Link]
  with Delete[Link]
  with Annotate[Link] {

  private val form = Link.form

  private val linkRoutes = controllers.linking.routes.Links

  private def redirectLink(src: Option[String], alt: Call): Call =
    src.map(r => controllers.admin.routes.Data.getItem(r)).getOrElse(alt)

  def get(id: String) = getAndRedirect(id, None)

  def getAndRedirect(id: String, redirect: Option[String] = None) = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.link.show(request.item, request.annotations, redirect))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
        VisibilityForm.form.fill(request.item.accessors.map(_.id)),
        request.users, request.groups,  linkRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }
  
  def update(id: String, redirect: Option[String] = None) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.link.edit(
      request.item, form.fill(request.item.model), linkRoutes.updatePost(id, redirect)))
  }

  def updatePost(id: String, redirect: Option[String] = None) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.link.edit(
          request.item, errorForm, linkRoutes.updatePost(id, redirect)))
      case Right(item) => Redirect(redirectLink(redirect, linkRoutes.get(id)))
        .flashing("success" -> "item.update.confirmation")
    }
  }  

  def delete(id: String, redirect: Option[String] = None) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, linkRoutes.deletePost(id, redirect),
        controllers.admin.routes.Data.getItem(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = DeleteAction(id).apply { implicit request =>
    Redirect(redirectLink(redirect, controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.promote(request.item, linkRoutes.promotePost(id)))
  }

  def promotePost(id: String) = PromoteItemAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.demote(request.item,
      linkRoutes.demotePost(id)))
  }

  def demotePost(id: String) = DemoteItemAction(id).apply { implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}