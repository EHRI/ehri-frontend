package controllers.core

import _root_.controllers.base.{LoginHandler, AuthController, Authorizer}
import play.api._
import play.api.mvc._
import views.html._
import jp.t2v.lab.play20.auth.{LoginLogout, Auth}
import play.api.Play._

object Application extends Controller with Auth with LoginLogout with Authorizer with AuthController {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout

  def index = userProfileAction { implicit userOpt => implicit request =>
    Secured {
      Ok(views.html.index("Your new application is ready."))
    }
  }
}

