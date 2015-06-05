package controllers.linking

import auth.AccountManager
import backend.rest.cypher.Cypher
import forms.VisibilityForm
import models.{LinkF, Link}
import javax.inject._
import controllers.generic._
import backend.Backend
import controllers.base.AdminController
import play.api.cache.CacheApi
import play.api.i18n.MessagesApi
import utils.MovedPageLookup
import views.MarkdownRenderer


case class Links @Inject()(
  implicit app: play.api.Application,
  cache: CacheApi,
  globalConfig: global.GlobalConfig,
  backend: Backend,
  accounts: AccountManager,
  pageRelocator: MovedPageLookup,
  messagesApi: MessagesApi,
  markdown: MarkdownRenderer,
  cypher: Cypher
) extends AdminController
  with Read[Link]
  with Visibility[Link]
  with Promotion[Link]
  with Update[LinkF, Link]
  with Delete[Link]
  with Annotate[Link] {

  private val form = Link.form

  private val linkRoutes = controllers.linking.routes.Links

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
  
  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.link.edit(
      request.item, form.fill(request.item.model), linkRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.link.edit(
          request.item, errorForm, linkRoutes.updatePost(id)))
      case Right(item) => Redirect(linkRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }  

  def delete(id: String, redirect: Option[String] = None) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, linkRoutes.deletePost(id, redirect),
        controllers.admin.routes.Admin.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = DeleteAction(id).apply { implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Admin.get(r))
        .getOrElse(controllers.admin.routes.Home.index()))
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