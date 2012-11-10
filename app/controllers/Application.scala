package controllers

import play.api._
import play.api.mvc._
import jp.t2v.lab.play20.auth.{Auth,LoginLogout}
import play.api.Play.current
import controllers.base.{Authorizer,LoginHandler}


object Application extends Controller with Auth with LoginLogout with Authorizer {

  lazy val loginHandler: LoginHandler = current.plugin(classOf[LoginHandler]).get
    
  def index = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.index("Your new application is ready."))
  }

  def test = optionalUserAction { implicit maybeUser =>
    implicit request =>
      Ok(views.html.test("Testing login here..."))
  }

  def login = loginHandler.login
  def loginPost = loginHandler.loginPost
  def logout = loginHandler.logout
}