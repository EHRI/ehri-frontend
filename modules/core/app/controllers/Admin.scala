package controllers.core

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.data.{FormError, Form}
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentType}
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import models.{UserProfile, UserProfileF}
import play.filters.csrf.CSRF
import controllers.base.{ControllerHelpers, AuthController}

import com.google.inject._
import play.api.data.FormError
import play.api.mvc.AsyncResult

class Admin @Inject()(implicit val globalConfig: global.GlobalConfig) extends Controller with AuthController with ControllerHelpers {

  lazy val userDAO: models.sql.UserDAO = play.api.Play.current.plugin(classOf[models.sql.UserDAO]).get

  val userPasswordForm = Form(
    tuple(
      "email" -> email,
      "username" -> nonEmptyText,
      "name" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying(Messages("login.passwordsDoNotMatch"), f => f match {
      case (_, _, _, pw, pwc) => pw == pwc
    })
  )

  val changePasswordForm = Form(
    tuple(
      "current" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying(Messages("login.passwordsDoNotMatch"), f => f match {
      case (_, pw, pwc) => pw == pwc
    })
  )

  val passwordLoginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  val groupMembershipForm = Form(single("group" -> list(nonEmptyText)))

  def adminActions = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.admin.actions())
  }

  def createUser = withContentPermission(PermissionType.Create, ContentType.UserProfile) {
      implicit userOpt => implicit request =>
    val csrf = CSRF.getToken(request)
    getGroups { groups =>
      Ok(views.html.admin.createUser(userPasswordForm, groupMembershipForm, groups,
        controllers.core.routes.Admin.createUserPost))
    }
  }

  def createUserPost = withContentPermission(PermissionType.Create, ContentType.UserProfile) {
      implicit userOpt => implicit request =>
    // TODO: Refactor to make this logic clearer...

    def createUserProfile(user: UserProfileF, groups: Seq[String])(f: UserProfile => Result): AsyncResult = {
      AsyncRest {
        rest.EntityDAO[UserProfile](EntityType.UserProfile, userOpt)
            .create[UserProfileF](user, params = Map("group" -> groups)).map { itemOrErr =>
          itemOrErr.right.map(f)
        }
      }
    }

    def grantOwnerPerms(profile: UserProfile)(f: => Result): AsyncResult = {
      AsyncRest {
        rest.PermissionDAO(userOpt).setItem(profile, ContentType.UserProfile,
            profile.id, List(PermissionType.Owner.toString)).map { permsOrErr =>
          permsOrErr.right.map { _ =>
            f
          }
        }
      }
    }

    val csrf = CSRF.getToken(request)
    userPasswordForm.bindFromRequest.fold(
      errorForm => {
        getGroups { groups =>
          Ok(views.html.admin.createUser(errorForm, groupMembershipForm.bindFromRequest,
              groups, controllers.core.routes.Admin.createUserPost))
        }
      },
      values => {
        val (email, username, name, pw, _) = values
        // check if the email is already registered...
        userDAO.findByEmail(email.toLowerCase).map { account =>
          val errForm = userPasswordForm.bindFromRequest
            .withError(FormError("email", Messages("admin.userEmailAlreadyRegistered", account.profile_id)))
          getGroups { groups =>
            BadRequest(views.html.admin.createUser(errForm, groupMembershipForm.bindFromRequest,
                groups, controllers.core.routes.Admin.createUserPost))
          }
        } getOrElse {
          // It's not registered, so create the account...
          val user = UserProfileF(id=None, identifier=username, name=name,
            location=None, about=None, languages=Nil)
          val groups = groupMembershipForm.bindFromRequest.value.getOrElse(List())

          createUserProfile(user, groups) { profile =>
            userDAO.create(email.toLowerCase, profile.id).map { account =>
              account.setPassword(BCrypt.hashpw(pw, BCrypt.gensalt))
              // Final step, grant user permissions on their own account
              grantOwnerPerms(profile) {
                // FIXME: Redirect here when we can!!! Redirect(routes.UserProfiles.get(profile.id))
                Redirect(controllers.core.routes.Admin.adminActions)
              }
            }.getOrElse {
              // FIXME: Handle this - probably by throwing a global error.
              // If it fails it'll probably die anyway...
              BadRequest("creating user account failed!")
            }
          }
        }
      }
    )
  }

  def passwordLogin = Action { implicit request =>
    Ok(views.html.pwLogin(passwordLoginForm, controllers.core.routes.Admin.passwordLoginPost))
  }

  def passwordLoginPost = Action { implicit request =>
    val action = controllers.core.routes.Admin.passwordLoginPost
    passwordLoginForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.pwLogin(errorForm, action))
      },
      data => {
        val (email, pw) = data
        userDAO.findByEmail(email.toLowerCase).flatMap { acc =>
          acc.password.flatMap { hashed =>
            if (BCrypt.checkpw(pw, hashed)) {
              Some(new controllers.core.Application().gotoLoginSucceeded(acc.profile_id))
            } else {
              None
            }
          }
        } getOrElse {
          Redirect(controllers.core.routes.Admin.passwordLogin).flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }

  //
  // Allow a logged-in user to change their account password.
  //
  def changePassword = userProfileAction { implicit user => implicit request =>
    Ok(views.html.pwChangePassword(changePasswordForm, controllers.core.routes.Admin.changePasswordPost))
  }

  def changePasswordPost = userProfileAction { implicit user => implicit request =>
    changePasswordForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.pwChangePassword(errorForm, controllers.core.routes.Admin.changePasswordPost))
      },
      data => {
        val (current, pw, _) = data
        user.flatMap(_.account.map(_.email)).flatMap { email =>
          println("Finding by email: " + email)
          userDAO.findByEmail(email.toLowerCase).flatMap { acc =>
            acc.password.flatMap { hashed =>
              if (BCrypt.checkpw(current, hashed)) {
                acc.updatePassword(BCrypt.hashpw(pw, BCrypt.gensalt))
                Some(Redirect(controllers.core.routes.Application.index).flashing("success" -> Messages("login.passwordChanged")))
              } else {
                None
              }
            }
          }
        } getOrElse {
          Redirect(controllers.core.routes.Admin.changePassword).flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }
}