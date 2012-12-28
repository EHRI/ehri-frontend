package controllers.base

import play.Plugin
import jp.t2v.lab.play20.auth.LoginLogout
import play.api.mvc._
import jp.t2v.lab.play20.auth.Auth

trait LoginHandler extends Plugin with Controller with Auth with LoginLogout with Authorizer {

  def login: Action[play.api.mvc.AnyContent]
  def loginPost: Action[play.api.mvc.AnyContent]

  def logout = optionalUserAction { implicit maybeUser =>
    implicit request =>
      gotoLogoutSucceeded
  }
}