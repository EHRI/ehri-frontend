package mocks

import controllers.base.LoginHandler
import play.api.Play.current
import play.api.mvc.Action
import controllers.routes


class MockLoginHandler(app: play.api.Application) extends LoginHandler {

  def login = Action {
    implicit request =>
    implicit val maybeUser = None
    // FIXME: When we try to render the *actual* form, Play locks up for
    // some reason...???
      Ok("test login") //views.html.login(models.forms.UserForm.openid, routes.Application.login))
  }

  def loginPost = Action { implicit request =>
    gotoLoginSucceeded(current.configuration.getString("test.user.profile_id").getOrElse("anonymous"))
  }
}