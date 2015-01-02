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

  def get(id: String) = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.annotation.show(request.item, request.annotations))
  }

  def history(id: String) = ItemHistoryAction(id).apply { implicit request =>
    Ok(views.html.admin.systemEvents.itemList(request.item, request.page, request.params))
  }

  def visibility(id: String) = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      forms.VisibilityForm.form.fill(request.item.accessors.map(_.id)),
      request.users, request.groups, annotationRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String) = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def update(id: String) = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.annotation.edit(
      request.item, form.fill(request.item.model), annotationRoutes.updatePost(id)))
  }

  def updatePost(id: String) = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.annotation.edit(
        request.item, errorForm, annotationRoutes.updatePost(id)))
      case Right(item) => Redirect(annotationRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String) = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, controllers.annotation.routes.Annotations.deletePost(id),
        controllers.annotation.routes.Annotations.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = DeleteAction(id).apply { implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Admin.get(r))
        .getOrElse(controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.promote(
      request.item, controllers.annotation.routes.Annotations.promotePost(id)))
  }

  def promotePost(id: String) = PromoteItemAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String) = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.demote(request.item,
      controllers.annotation.routes.Annotations.demotePost(id)))
  }

  def demotePost(id: String) = DemoteItemAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}