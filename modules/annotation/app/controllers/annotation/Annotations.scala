package controllers.annotation

import models.{AccountDAO, Annotation}
import com.google.inject._
import controllers.generic.{Annotate, Delete, Read, Visibility}
import backend.Backend


case class Annotations @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO) extends Read[Annotation]
  with Visibility[Annotation]
  with Delete[Annotation]
  with Annotate[Annotation] {

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.annotation.details(item, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
      forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, controllers.annotation.routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, controllers.annotation.routes.Annotations.deletePost(id),
        controllers.annotation.routes.Annotations.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Admin.get(r))
        .getOrElse(globalConfig.routeRegistry.default))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String) = promoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.permissions.promote(item, controllers.annotation.routes.Annotations.promotePost(id)))
  }

  def promotePost(id: String) = promotePostAction(id) { item => bool => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String) = demoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.permissions.demote(item,
      controllers.annotation.routes.Annotations.demotePost(id)))
  }

  def demotePost(id: String) = demotePostAction(id) { item => bool => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}