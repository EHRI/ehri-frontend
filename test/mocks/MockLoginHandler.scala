package mocks

import controllers.base.LoginHandler
import play.api.Play.current
import play.api.mvc.Action
import controllers.routes
import play.api.data.Forms._
import play.api.data.Form
import com.google.inject.{Inject, Singleton}


/**
 * Mock loginhandler implementation.
 * @param globalConfig
 */
@Singleton
case class MockLoginHandler @Inject()(implicit globalConfig: global.GlobalConfig) extends LoginHandler {

  def login = Action { implicit request =>
    implicit val userOpt = None
    Ok(views.html.login(forms.OpenIDForm.openid, controllers.core.routes.Application.login))
  }

  def loginPost = Action { implicit request =>
    val profile = Form(single("profile" -> nonEmptyText)).bindFromRequest().get
    gotoLoginSucceeded(profile)
  }
}