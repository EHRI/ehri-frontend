package controllers.annotation

import controllers.AppComponents
import controllers.base.AdminController
import controllers.generic._
import javax.inject._
import models.Annotation
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import services.data.DataHelpers
import utils.RangeParams


case class Annotations @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents,
  dataHelpers: DataHelpers
) extends AdminController
  with Read[Annotation]
  with Visibility[Annotation]
  with Promotion[Annotation]
  with Update[Annotation]
  with Delete[Annotation]
  with Annotate[Annotation] {

  private val form = Annotation.form
  private val annotationRoutes = controllers.annotation.routes.Annotations

  def get(id: String): Action[AnyContent] = ItemMetaAction(id).apply { implicit request =>
    Ok(views.html.admin.annotation.show(request.item, request.annotations))
  }

  def history(id: String, range: RangeParams): Action[AnyContent] = ItemHistoryAction(id, range).apply { implicit request =>
    Ok(views.html.admin.systemEvent.itemList(request.item, request.page, request.params))
  }

  def visibility(id: String): Action[AnyContent] = EditVisibilityAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.visibility(request.item,
      forms.visibilityForm.fill(request.item.accessors.map(_.id)),
      request.usersAndGroups, annotationRoutes.visibilityPost(id)))
  }

  def visibilityPost(id: String): Action[AnyContent] = UpdateVisibilityAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.update.confirmation")
  }

  def update(id: String): Action[AnyContent] = EditAction(id).apply { implicit request =>
    Ok(views.html.admin.annotation.edit(
      request.item, form.fill(request.item.data), annotationRoutes.updatePost(id)))
  }

  def updatePost(id: String): Action[AnyContent] = UpdateAction(id, form).apply { implicit request =>
    request.formOrItem match {
      case Left(errorForm) => BadRequest(views.html.admin.annotation.edit(
        request.item, errorForm, annotationRoutes.updatePost(id)))
      case Right(item) => Redirect(annotationRoutes.get(id))
        .flashing("success" -> "item.update.confirmation")
    }
  }

  def delete(id: String): Action[AnyContent] = CheckDeleteAction(id).apply { implicit request =>
    Ok(views.html.admin.delete(
      request.item, controllers.annotation.routes.Annotations.deletePost(id),
        controllers.annotation.routes.Annotations.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None): Action[AnyContent] = DeleteAction(id).apply { implicit request =>
    Redirect(redirect.map(r => controllers.admin.routes.Data.getItem(r))
        .getOrElse(controllers.admin.routes.Home.index()))
        .flashing("success" -> "item.delete.confirmation")
  }

  def promote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.promote(
      request.item, controllers.annotation.routes.Annotations.promotePost(id)))
  }

  def promotePost(id: String): Action[AnyContent] = PromoteItemAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.promote.confirmation")
  }

  def demote(id: String): Action[AnyContent] = EditPromotionAction(id).apply { implicit request =>
    Ok(views.html.admin.permissions.demote(request.item,
      controllers.annotation.routes.Annotations.demotePost(id)))
  }

  def demotePost(id: String): Action[AnyContent] = DemoteItemAction(id).apply { implicit request =>
    Redirect(controllers.annotation.routes.Annotations.get(id))
      .flashing("success" -> "item.demote.confirmation")
  }
}
