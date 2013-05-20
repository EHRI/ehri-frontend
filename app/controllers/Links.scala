package controllers

import defines._
import models.{AccessPointF, LinkF, UserProfile, Link}
import base.{EntityDelete, EntityAnnotate, VisibilityController, EntityRead}
import play.api.i18n.Messages
import play.api.libs.json.{JsValue, JsError, Json, JsPath}
import play.api.data.validation.ValidationError
import play.api.mvc._
import models.base.LinkableEntity
import play.mvc.BodyParser.AnyContent
import scala.Some
import play.api.http.ContentTypes


object Links extends EntityRead[Link]
  with VisibilityController[Link]
  with EntityDelete[Link]
  with EntityAnnotate[Link] {

  val entityType = EntityType.Link
  val contentType = ContentType.Link

  def get(id: String) = getAction(id) { item => links => _ => implicit userOpt => implicit request =>
    Ok(views.html.link.show(Link(item), links))

  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(Link(item), page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) { item => users => groups => implicit userOpt =>
    implicit request =>
      Ok(views.html.permissions.visibility(Link(item),
        models.forms.VisibilityForm.form.fill(Link(item).accessors.map(_.id)),
        users, groups, routes.Links.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt =>
    implicit request =>
      Redirect(routes.Links.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      Link(item), routes.Links.deletePost(id), routes.Concepts.get(id)))
  }

  def deletePost(id: String) = deletePostAction(id) {
    // TODO: Work out how to redirect to somewhere useful...
    ok => implicit userOpt => implicit request =>
      Redirect(routes.Application.index)
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }

  def annotate(id: String) = annotationAction(id) {
      item => form => implicit userOpt => implicit request =>
    Ok(views.html.link.annotate(Link(item), form, routes.Links.annotatePost(id)))
  }

  def annotatePost(id: String) = annotationPostAction(id) { formOrLink => implicit userOpt =>
    implicit request =>
      formOrLink match {
        case Left(errorForm) => getEntity(id, userOpt) { item =>
          BadRequest(views.html.link.annotate(Link(item),
            errorForm, routes.Links.annotatePost(id)))
        }
        case Right(link) => {
          Redirect(routes.Links.get(id))
            .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
        }
      }
  }
}