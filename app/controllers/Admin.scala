package controllers

import play.api.mvc._
import jp.t2v.lab.play20.auth.{ Auth, LoginLogout }
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import base.{ControllerHelpers, Authorizer, AuthController, LoginHandler}
import play.api.data.Form
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentType}
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import models.forms.UserProfileF
import models.sql.OpenIDUser

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
      Ok(views.html.admin.createUser(userPasswordForm, routes.Admin.createUserPost))
  }

  def createUserPost = withContentPermission(PermissionType.Create, ContentType.UserProfile) { implicit user =>
    implicit request =>

      implicit val maybeUser = Some(user)
      userPasswordForm.bindFromRequest.fold(
        errorForm => {
          Ok(views.html.admin.createUser(errorForm, routes.Admin.createUserPost))
        },
        values => {
          val (email, username, name, pw, _, groups) = values
          val user = UserProfileF(id=None, identifier=username, name=name,
            location=None, about=None, languages=None)
          AsyncRest {
            rest.EntityDAO(EntityType.UserProfile, maybeUser).create(user).map { itemOrErr =>
              itemOrErr.right.map { entity =>
                models.sql.OpenIDUser.create(email, entity.id).map { user =>
                  user.setPassword(BCrypt.hashpw(pw, BCrypt.gensalt))
                  Redirect(routes.UserProfiles.get(entity.id))
                }.getOrElse {
                  // FIXME: Handle this
                  BadRequest("creating user account failed!")
                }
              }
            }
          }
        }
      )
  }


  def passwordLogin = Action { implicit request =>
    val form = Form(
      tuple(
        "email" -> email,
        "password" -> nonEmptyText
      )
    )
    Ok(views.html.pwLogin(form, routes.Admin.passwordLoginPost))
  }

  def passwordLoginPost = Action { implicit request =>
    val form = Form(
      tuple(
        "email" -> email,
        "password" -> nonEmptyText
      )
    ).bindFromRequest
    val action = routes.Admin.passwordLoginPost

    form.fold(
      errorForm => {
        BadRequest(views.html.pwLogin(errorForm, action))
      },
      data => {
        val (email, pw) = data
        OpenIDUser.findByEmail(email).flatMap { acc =>
          acc.password.flatMap { hashed =>
            if (BCrypt.checkpw(pw, hashed)) {
              Some(Application.gotoLoginSucceeded(acc.profile_id))
            } else {
              None
            }
          }
        } getOrElse {
          Redirect(routes.Admin.passwordLogin).flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }

}