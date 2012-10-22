package mocks

import play.api._
import play.api.mvc._
import controllers.LoginHandler
import controllers.LoginHandler

class MockLoginHandler(app: play.api.Application) extends LoginHandler {

  def login = Action {
    implicit request =>
      Ok("Mock login form")
  }

  def loginPost = Action { implicit request =>
    gotoLoginSucceeded("mike")
  }
}