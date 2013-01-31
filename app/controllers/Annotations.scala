package controllers

import defines._
import models.{Annotation, SystemEvent}
import base.{AnnotationController, VisibilityController, EntityRead}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages


object Annotations extends EntityRead[Annotation]
  with VisibilityController[Annotation]
  with AnnotationController[Annotation] {

  val entityType = EntityType.Annotation
  val contentType = ContentType.Annotation

  val builder = Annotation.apply _

  def get(id: String) = getAction(id) { item => annotations => implicit maybeUser => implicit request =>
    Ok(views.html.annotation.show(Annotation(item), annotations))
  }

  def history(id: String) = historyAction(id) { item => page => implicit maybeUser => implicit request =>
    Ok(views.html.systemEvents.itemList(Annotation(item), page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.permissions.visibility(Annotation(item),
        models.forms.VisibilityForm.form.fill(Annotation(item).accessors.map(_.id)),
        users, groups, routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def annotate(id: String) = withItemPermission(id, PermissionType.Annotate, contentType) { item => implicit user =>
    implicit request =>
      Ok(views.html.annotation.annotate(Annotation(item),
        models.forms.AnnotationForm.form, routes.Annotations.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrAnnotation => implicit user =>
    implicit request =>
      formOrAnnotation match {
        case Left(errorForm) => getEntity(id, Some(user)) { item =>
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