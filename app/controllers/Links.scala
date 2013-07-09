package controllers

import defines._
import forms.VisibilityForm
import models._
import controllers.base._
import play.api.i18n.Messages


object Links extends EntityRead[LinkMeta]
  with VisibilityController[LinkMeta]
  with EntityDelete[LinkMeta]
  with EntityAnnotate[LinkMeta] {

  val entityType = EntityType.Link
  val contentType = ContentType.Link

  def get(id: String, redirect: Option[String] = None) = getAction(id) { item => links => _ => implicit userOpt => implicit request =>
    Ok(views.html.link.show(item, links, redirect))
  }

  def history(id: String) = historyAction(id) { item => page => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, ListParams()))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups, routes.Links.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(routes.Links.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String, redirect: Option[String] = None) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, routes.Links.deletePost(id, redirect), routes.Application.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(redirect.map(r => routes.Application.get(r)).getOrElse(routes.Search.search))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}