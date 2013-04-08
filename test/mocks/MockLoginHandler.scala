package mocks

import controllers.base.LoginHandler
import play.api.Play.current
import play.api.mvc.Action
import controllers.routes
import play.api.data.Forms._
import play.api.data.Form


/**
 * Mock loginhandler implementation.
 * @param app
 */
class MockLoginHandler(app: play.api.Application) extends LoginHandler {

  def login = Action { implicit request =>
    implicit val userOpt = None
    Ok(views.html.login(models.forms.OpenIDForm.openid, routes.Application.login))
  }

  def loginPost = Action { implicit request =>
    val profile = Form(single("profile" -> nonEmptyText)).bindFromRequest().get
    gotoLoginSucceeded(profile)
  }
}