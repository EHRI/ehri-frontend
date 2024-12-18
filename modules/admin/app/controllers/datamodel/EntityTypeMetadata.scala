package controllers.datamodel

import controllers.AppComponents
import controllers.base.{AdminController, ApiBodyParsers}
import play.api.mvc._

import javax.inject._


@Singleton
case class EntityTypeMetadata @Inject()(
  controllerComponents: ControllerComponents,
  appComponents: AppComponents
) extends AdminController with ApiBodyParsers {

  def index(): Action[AnyContent] = WithUserAction { implicit request =>
    Redirect(controllers.datamodel.routes.EntityTypeMetadata.editor())
  }

  def editor(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.admin.datamodel.editor())
  }

  def auditor(): Action[AnyContent] = WithUserAction { implicit request =>
    Ok(views.html.admin.datamodel.auditor())
  }
}
