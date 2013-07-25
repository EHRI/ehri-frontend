package controllers.core

import defines._
import models.Annotation
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages
import controllers.ListParams
import controllers.base.{EntityRead, VisibilityController, EntityDelete, EntityAnnotate}

import com.google.inject._
import global.GlobalConfig

class Annotations @Inject()(val globalConfig: GlobalConfig) extends EntityRead[Annotation]
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
        forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.core.routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(controllers.core.routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, controllers.core.routes.Annotations.deletePost(id),
        controllers.core.routes.Annotations.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
    // TODO: Work out how to redirect to somewhere useful...
    ok => implicit userOpt => implicit request =>
      Redirect(controllers.core.routes.Application.index)
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}