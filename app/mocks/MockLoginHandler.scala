package mocks

import controllers.base.LoginHandler
import play.api.Play.current
import play.api.mvc._


class MockLoginHandler(app: play.api.Application) extends LoginHandler {

  def login = Action {
    implicit request =>
      Ok(views.html.mocks.mockLoginForm(controllers.routes.Application.loginPost, request))
  }

  def loginPost = Action { implicit request =>
    gotoLoginSucceeded(current.configuration.getString("test.user.profile_id").getOrElse("anonymous"))
  }
}