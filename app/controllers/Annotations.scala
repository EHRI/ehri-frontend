package controllers

import defines._
import models.{Annotation, ActionLog}
import base.{VisibilityController, EntityRead}
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages


object Annotations extends EntityRead[Annotation]
  with VisibilityController[Annotation] {

  val entityType = EntityType.Annotation
  val contentType = ContentType.Annotation

  val builder = Annotation.apply _

  def get(id: String, page: Int = 1, limit: Int = DEFAULT_LIMIT) = getAction(id) { item => annotations =>
    implicit maybeUser =>
      implicit request =>
      Ok(views.html.annotation.show(Annotation(item), annotations))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit user =>
    implicit request =>
      Ok(views.html.visibility(Annotation(item), users, groups, routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit user =>
    implicit request =>
      Redirect(routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }


}