package controllers.annotation

import defines._
import models.Annotation
import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages
import controllers.base.{EntityRead, VisibilityController, EntityDelete, EntityAnnotate}

import com.google.inject._
import global.GlobalConfig
import models.json.RestResource

class Annotations @Inject()(implicit val globalConfig: GlobalConfig) extends EntityRead[Annotation]
  with VisibilityController[Annotation]
  with EntityDelete[Annotation]
  with EntityAnnotate[Annotation] {

  implicit def resource = new RestResource[Annotation] {
    val entityType = EntityType.Annotation
  }

  val entityType = EntityType.Annotation
  val contentType = ContentTypes.Annotation

  def get(id: String) = getAction(id) { item => annotations => links => implicit userOpt => implicit request =>
    Ok(views.html.annotation.show(item, annotations))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.permissions.visibility(item,
        forms.VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, controllers.annotation.routes.Annotations.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(controllers.annotation.routes.Annotations.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, controllers.annotation.routes.Annotations.deletePost(id),
        controllers.annotation.routes.Annotations.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(redirect.map(r => controllers.core.routes.Application.get(r))
        .getOrElse(globalConfig.routeRegistry.default))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}