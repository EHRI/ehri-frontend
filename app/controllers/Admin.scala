package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current

import base.{ControllerHelpers, Authorizer, AuthController, LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import defines.{PermissionType,ContentType}

object Admin extends Controller with AuthController with ControllerHelpers {

  def adminActions = adminAction { implicit user =>
    implicit request =>
      Ok("todo")
  }

  def createUser = withContentPermission(PermissionType.Create, ContentType.UserProfile) { implicit user =>
    implicit request =>
      Ok("todo")
  }

  def createUserPost = withContentPermission(PermissionType.Create, ContentType.UserProfile) { implicit user =>
    implicit request =>
      Ok("todo")
  }

}