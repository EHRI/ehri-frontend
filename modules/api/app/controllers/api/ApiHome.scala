package controllers.api

import javax.inject.{Inject, Singleton}

import controllers.AppComponents
import controllers.portal.base.PortalController
import play.api.mvc.ControllerComponents


@Singleton
case class ApiHome @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends PortalController {
  def index = OptionalUserAction { implicit request =>
    Ok(views.html.api.docs())
  }
}
