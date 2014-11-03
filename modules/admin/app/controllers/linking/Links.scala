package controllers.linking

import forms.VisibilityForm
import models.{LinkF, AccountDAO, Link}
import com.google.inject._
import controllers.generic._
import backend.Backend


case class Links @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Read[Link]
  with Visibility[Link]
  with Update[LinkF, Link]
  with Delete[Link]
  with Annotate[Link] {

  private val form = Link.form

  private val linkRoutes = controllers.linking.routes.Links

  def get(id: String) = getAndRedirect(id, None)

  def getAndRedirect(id: String, redirect: Option[String] = None) = getAction(id) { item => links => _ => implicit userOpt => implicit request =>
    Ok(views.html.link.show(item, links, redirect))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups,  linkRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(linkRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
  }
  
  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.link.edit(item, form.fill(item.model), linkRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.link.edit(
          olditem, errorForm, linkRoutes.updatePost(id)))
      case Right(item) => Redirect(linkRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }  

  def delete(id: String, redirect: Option[String] = None) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, linkRoutes.deletePost(id, redirect),
        controllers.admin.routes.Admin.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      implicit userOpt => implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Admin.get(r))
        .getOrElse(controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }


  def promote(id: String) = promoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.permissions.promote(item, linkRoutes.promotePost(id)))
  }

  def promotePost(id: String) = promotePostAction(id) { item => bool => implicit userOpt => implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String) = demoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.permissions.demote(item,
      linkRoutes.demotePost(id)))
  }

  def demotePost(id: String) = demotePostAction(id) { item => bool => implicit userOpt => implicit request =>
    Redirect(linkRoutes.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}