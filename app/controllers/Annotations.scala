package controllers

import defines._
import models.Annotation
import base.{EntityDelete, EntityAnnotate, VisibilityController, EntityRead}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages


object Annotations extends EntityRead[Annotation]
  with VisibilityController[Annotation]
  with EntityDelete[Annotation]
  with EntityAnnotate[Annotation] {

  val entityType = EntityType.Annotation
  val contentType = ContentType.Annotation

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.annotation.show(item, annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.permissions.visibility(item,
        forms.VisibilityForm.form.fill(List.empty[String]), //item.accessors.map(_.id)),
        users, groups, routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, routes.Annotations.deletePost(id), routes.Concepts.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
    // TODO: Work out how to redirect to somewhere useful...
    ok => implicit userOpt => implicit request =>
      Redirect(controllers.core.routes.Application.index)
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}