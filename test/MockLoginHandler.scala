


import play.api._
import play.api.mvc._
import controllers.LoginHandler
import jp.t2v.lab.play20.auth.LoginLogout
import controllers.Authorizer
import controllers.Authorizer
import controllers.LoginHandler

class MockLoginHandler(app: play.api.Application) extends LoginHandler with Controller with LoginLogout with Authorizer {

  def login = Action {
    implicit request =>
      gotoLoginSucceeded("mike")
  }

  def loginPost = Action { implicit request =>
    gotoLoginSucceeded("mike")
  }
}