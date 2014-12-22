package controllers.annotation

import models.{AnnotationF, AccountDAO, Annotation}
import com.google.inject._
import controllers.generic._
import backend.Backend
import controllers.base.AdminController

case class Annotations @Inject()(implicit globalConfig: global.GlobalConfig, backend: Backend, userDAO: AccountDAO)
  extends AdminController
  with Read[Annotation]
  with Visibility[Annotation]
  with Promotion[Annotation]
  with Update[AnnotationF,Annotation]
  with Delete[Annotation]
  with Annotate[Annotation] {

  private val form = Annotation.form
  private val annotationRoutes = controllers.annotation.routes.Annotations

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.admin.annotation.show(item, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.admin.systemEvents.itemList(item, page, params))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.visibility(item,
      forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
      users, groups, annotationRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def update(id: String) = updateAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.annotation.edit(item, form.fill(item.model), annotationRoutes.updatePost(id)))
  }

  def updatePost(id: String) = updatePostAction(id, form) { olditem => formOrItem => implicit userOpt => implicit request =>
    formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.annotation.edit(
        olditem, errorForm, annotationRoutes.updatePost(id)))
      case Right(item) => Redirect(annotationRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.delete(
      item, controllers.annotation.routes.Annotations.deletePost(id),
        controllers.annotation.routes.Annotations.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      implicit userOpt => implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Admin.get(r))
        .getOrElse(controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String) = promoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.promote(item, controllers.annotation.routes.Annotations.promotePost(id)))
  }

  def promotePost(id: String) = promotePostAction(id) { item => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String) = promoteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.admin.permissions.demote(item,
      controllers.annotation.routes.Annotations.demotePost(id)))
  }

  def demotePost(id: String) = demotePostAction(id) { item => implicit userOpt => implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}