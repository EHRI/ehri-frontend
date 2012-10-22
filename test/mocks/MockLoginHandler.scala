package mocks

import controllers.LoginHandler
import play.api.Play.current
import play.api.mvc.Action


class MockLoginHandler(app: play.api.Application) extends LoginHandler {

  def login = Action {
    implicit request =>
      Ok("Mock login form")
  }

  def loginPost = Action { implicit request =>
    gotoLoginSucceeded(current.configuration.getString("test.user.profile_id").getOrElse("anonymous"))
  }
}