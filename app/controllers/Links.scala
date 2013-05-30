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

  def get(id: String, redirect: Option[String] = None) = getAction(id) { item => links => _ => implicit userOpt => implicit request =>
    Ok(views.html.link.show(Link(item), links, redirect))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(Link(item), page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(Link(item),
        models.forms.VisibilityForm.form.fill(Link(item).accessors.map(_.id)),
        users, groups, routes.Links.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Links.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String, redirect: Option[String] = None) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      Link(item), routes.Links.deletePost(id, redirect), routes.Application.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(redirect.map(r => routes.Application.get(r)).getOrElse(routes.Search.search))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}