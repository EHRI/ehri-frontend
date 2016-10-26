package controllers.api

import javax.inject.{Inject, Singleton}

import controllers.Components
import controllers.portal.base.PortalController


@Singleton
case class ApiHome @Inject()(components: Components) extends PortalController {
  def index = OptionalUserAction { implicit request =>
    Ok(views.html.api.docs())
  }
}
