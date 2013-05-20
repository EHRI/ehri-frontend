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
    Ok(views.html.annotation.show(Annotation(item), annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(Annotation(item), page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.permissions.visibility(Annotation(item),
        models.forms.VisibilityForm.form.fill(Annotation(item).accessors.map(_.id)),
        users, groups, routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      Annotation(item), routes.Annotations.deletePost(id), routes.Concepts.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
    // TODO: Work out how to redirect to somewhere useful...
    ok => implicit userOpt => implicit request =>
      Redirect(routes.Application.index)
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def annotate(id: String) = annotationAction(id) {
      item => form => implicit userOpt => implicit request =>
    Ok(views.html.annotation.annotate(Annotation(item), form, routes.Annotations.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit userOpt =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, userOpt) { item =>
          BadRequest(views.html.annotation.annotate(Annotation(item),
            errorForm, routes.Annotations.annotatePost(id)))
        }
        case Right(annotation) => {
          Redirect(routes.Annotations.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }
}