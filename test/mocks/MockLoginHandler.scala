package mocks

import controllers.base.LoginHandler
import play.api.Play.current
import play.api.mvc.Action
import controllers.routes
import play.api.data.Forms._
import play.api.data.Form


/**
 * Mock loginhandler implementation.
 * @param menuConfig
 */
case class MockLoginHandler(implicit menuConfig: global.MenuConfig) extends LoginHandler {

  def login = Action { implicit request =>
    implicit val userOpt = None
    Ok(views.html.login(forms.OpenIDForm.openid, controllers.core.routes.Application.login))
  }

  def loginPost = Action { implicit request =>
    val profile = Form(single("profile" -> nonEmptyText)).bindFromRequest().get
    gotoLoginSucceeded(profile)
  }
}