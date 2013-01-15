package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import base.{ControllerHelpers, Authorizer, AuthController, LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import defines.{PermissionType,ContentType}
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import models.forms.UserProfileF

object Admin extends Controller with AuthController with ControllerHelpers {

  val userPasswordForm = Form(
    tuple(
      "email" -> email,
      "username" -> nonEmptyText,
      "name" -> nonEmptyText,
      "password" -> nonEmptyText,
      "confirm" -> nonEmptyText,
      "groups" -> optional(list(nonEmptyText))

    ) verifying(Messages("admin.passwordsDoNotMatch"), f => f match {
      case (_, _, _, pw, pwc, _) => pw == pwc
    })
  )

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

      userPasswordForm.bindFromRequest.fold(
        errorForm => {
          BadRequest(errorForm.errors.toString)
        },
        values => {
          val (email, username, name, pw, _, groups) = values
          val user = UserProfileF(id=None, identifier=username, name=name, location=None, about=None, languages=Nil)
          Async {
            rest.AdminDAO().createNewUserProfile.map {
              case Right(entity) => {
                models.sql.OpenIDUser.create(email, entity.id).map { user =>
                  user.setPassword(BCrypt.hashpw(pw, BCrypt.gensalt))
                  Application.gotoLoginSucceeded(user.profile_id)
                }.getOrElse(BadRequest("Creation of user db failed!"))
              }
              case Left(err) => {
                BadRequest("Unexpected REST error: " + err)
              }
            }
          }
        }
      )
  }

}