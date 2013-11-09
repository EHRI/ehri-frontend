package controllers.linking

import defines._
import forms.VisibilityForm
import models._
import play.api.i18n.Messages

import com.google.inject._
import global.GlobalConfig
import controllers.generic.{Annotate, Delete, Read, Visibility}

case class Links @Inject()(implicit globalConfig: GlobalConfig, backend: rest.Backend) extends Read[Link]
  with Visibility[Link]
  with Delete[Link]
  with Annotate[Link] {

  implicit val resource = Link.Resource

  val entityType = EntityType.Link
  val contentType = ContentTypes.Link

  def get(id: String) = getAndRedirect(id, None)

  def getAndRedirect(id: String, redirect: Option[String] = None) = getAction(id) { item => links => _ => implicit userOpt => implicit request =>
    Ok(views.html.link.show(item, links, redirect))
  }

  def history(id: String) = historyAction(id) {
      item => page => params => implicit userOpt => implicit request =>
    Ok(views.html.systemEvents.itemList(item, page, params))
  }

  def visibility(id: String) = visibilityAction(id) {
      item => users => groups => implicit userOpt => implicit request =>
    Ok(views.html.permissions.visibility(item,
        VisibilityForm.form.fill(item.accessors.map(_.id)),
        users, groups,  controllers.linking.routes.Links.visibilityPost(id)))
  }

  def visibilityPost(id: String) = visibilityPostAction(id) { ok => implicit userOpt => implicit request =>
    Redirect(controllers.linking.routes.Links.get(id))
        .flashing("success" -> Messages("confirmations.itemWasUpdated", id))
  }

  def delete(id: String, redirect: Option[String] = None) = deleteAction(id) { item => implicit userOpt => implicit request =>
    Ok(views.html.delete(
      item, controllers.linking.routes.Links.deletePost(id, redirect),
        controllers.core.routes.Application.get(id)))
  }

  def deletePost(id: String, redirect: Option[String] = None) = deletePostAction(id) {
      ok => implicit userOpt => implicit request =>
    Redirect(redirect.map(r => controllers.core.routes.Application.get(r))
        .getOrElse(globalConfig.routeRegistry.default))
        .flashing("success" -> Messages("confirmations.itemWasDeleted", id))
  }
}