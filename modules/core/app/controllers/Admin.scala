package controllers.core

import play.api.mvc._
import play.api.libs.concurrent.Execution.Implicits._

import play.api.data.{Forms, Form, FormError}
import play.api.data.Forms._
import defines.{EntityType, PermissionType, ContentTypes}
import play.api.i18n.Messages
import org.mindrot.jbcrypt.BCrypt
import models.{Account, AccountDAO, UserProfile, UserProfileF}
import controllers.base.{ControllerHelpers, AuthController}

import com.google.inject._
import play.api.mvc.AsyncResult
import jp.t2v.lab.play20.auth.LoginLogout
import java.util.UUID
import play.api.Play.current
import scala.concurrent.Future
import play.api.libs.ws.WS
import play.api.Logger

/**
 * Controller for handling user admin actions.
 * @param globalConfig
 */
class Admin @Inject()(implicit val globalConfig: global.GlobalConfig) extends Controller with AuthController with ControllerHelpers with LoginLogout {

  private lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

  private val userPasswordForm = Form(
    tuple(
      "email" -> email,
      "username" -> nonEmptyText,
      "name" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, _, _, pw, pwc) => pw == pwc
    })
  )

  private val changePasswordForm = Form(
    tuple(
      "current" -> nonEmptyText,
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, pw, pwc) => pw == pwc
    })
  )

  private val passwordLoginForm = Form(
    tuple(
      "email" -> email,
      "password" -> nonEmptyText
    )
  )

  private val groupMembershipForm = Form(single("group" -> list(nonEmptyText)))

  /**
   * Show the admin home page.
   * @return
   */
  def adminActions = adminAction { implicit userOpt => implicit request =>
    Ok(views.html.admin.actions())
  }

  /**
   * Create a user's account for them with a pre-set password. This is an
   * admin only function and should be removed eventually.
   * @return
   */
  def createUser = withContentPermission(PermissionType.Create, ContentTypes.UserProfile) {
      implicit userOpt => implicit request =>
    getGroups { groups =>
      Ok(views.html.admin.createUser(userPasswordForm, groupMembershipForm, groups,
        controllers.core.routes.Admin.createUserPost))
    }
  }

  /**
   * Create a user. Currently this gets a bit gnarly.
   * @return
   */
  def createUserPost = withContentPermission(PermissionType.Create, ContentTypes.UserProfile) {
      implicit userOpt => implicit request =>

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
        rest.PermissionDAO(userOpt).setItem(profile, ContentTypes.UserProfile,
            profile.id, List(PermissionType.Owner.toString)).map { permsOrErr =>
          permsOrErr.right.map { _ =>
            f
          }
        }
      }
    }

    def saveUser(email: String, username: String, name: String, pw: String): AsyncResult = {
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
        val user = UserProfileF(id = None, identifier = username, name = name,
          location = None, about = None, languages = Nil)
        val groups = groupMembershipForm.bindFromRequest.value.getOrElse(List())

        createUserProfile(user, groups) { profile =>
          userDAO.create(email.toLowerCase, profile.id).map { account =>
            account.setPassword(BCrypt.hashpw(pw, BCrypt.gensalt))
            // Final step, grant user permissions on their own account
            grantOwnerPerms(profile) {
              Redirect(controllers.core.routes.Admin.adminActions)
            }
          }.getOrElse {
            sys.error("Unable to create user profile on database. Probably a programming error...")
          }
        }
      }
    }

    userPasswordForm.bindFromRequest.fold(
      errorForm => {
        getGroups { groups =>
          Ok(views.html.admin.createUser(errorForm, groupMembershipForm.bindFromRequest,
              groups, controllers.core.routes.Admin.createUserPost))
        }
      },
      values => {
        val (email, username, name, pw, _) = values
        saveUser(email, username, name, pw)
      }
    )
  }

  /**
   * Login via a password...
   * @return
   */
  def passwordLogin = Action { implicit request =>
    Ok(views.html.admin.pwLogin(passwordLoginForm, controllers.core.routes.Admin.passwordLoginPost))
  }

  /**
   * Check password and store credentials.
   * @return
   */
  def passwordLoginPost = Action { implicit request =>
    val action = controllers.core.routes.Admin.passwordLoginPost
    passwordLoginForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.admin.pwLogin(errorForm, action))
      },
      data => {
        val (email, pw) = data

        (for {
          account <- userDAO.findByEmail(email.toLowerCase)
          hashedPw <- account.password if BCrypt.checkpw(pw, hashedPw)
        } yield gotoLoginSucceeded(account.profile_id)) getOrElse {
          Redirect(controllers.core.routes.Admin.passwordLogin)
            .flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }

  /**
   * Allow a logged in user to change their password.
   * @return
   */
  def changePassword = userProfileAction { implicit user => implicit request =>
    Ok(views.html.admin.pwChangePassword(
      changePasswordForm, controllers.core.routes.Admin.changePasswordPost))
  }

  /**
   * Store a changed password.
   * @return
   */
  def changePasswordPost = userProfileAction { implicit userOpt => implicit request =>
    changePasswordForm.bindFromRequest.fold(
      errorForm => {
        BadRequest(views.html.admin.pwChangePassword(errorForm,
          controllers.core.routes.Admin.changePasswordPost))
      },
      data => {
        val (current, pw, _) = data

        (for {
          user <- userOpt
          account <- user.account
          hashedPw <- account.password if BCrypt.checkpw(current, hashedPw)
        } yield {
          account.updatePassword(BCrypt.hashpw(pw, BCrypt.gensalt))
          Redirect(globalConfig.routeRegistry.default)
            .flashing("success" -> Messages("login.passwordChanged"))
        }) getOrElse {
          Redirect(controllers.core.routes.Admin.changePassword)
            .flashing("error" -> Messages("login.badUsernameOrPassword"))
        }
      }
    )
  }

  private val forgotPasswordForm = Form(Forms.single("email" -> email))

  private def sendResetEmail(email: String, uuid: UUID) {
    import com.typesafe.plugin._
    use[MailerPlugin].email
      .setSubject("EHRI Password Reset")
      .addRecipient(email)
      .addFrom("EHRI Password Reset <noreply@ehri-project.eu>")
      .send( "text", s"<html>$uuid</html>")
  }

  def forgotPassword = Action { implicit request =>
    val recaptchaKey = play.api.Play.configuration.getString("recaptcha.key.public").getOrElse("fakekey")
    Ok(views.html.admin.forgotPassword(forgotPasswordForm,
      recaptchaKey, controllers.core.routes.Admin.forgotPasswordPost))
  }

  def forgotPasswordPost = Action { implicit request =>
    val recaptchaKey = play.api.Play.configuration.getString("recaptcha.key.public").getOrElse("fakekey")

    Async {
      checkRecapture.map { ok =>
        if (!ok) {
          val form = forgotPasswordForm.withGlobalError("error.badRecapture")
          BadRequest(views.html.admin.forgotPassword(form,
            recaptchaKey, controllers.core.routes.Admin.forgotPasswordPost))
        } else {
          forgotPasswordForm.bindFromRequest.fold({ errForm =>
            BadRequest(views.html.admin.forgotPassword(errForm,
              recaptchaKey, controllers.core.routes.Admin.forgotPasswordPost))
          }, { email =>
            userDAO.findByEmail(email).map { account =>
              val uuid = UUID.randomUUID()
              userDAO.createResetToken(uuid, account.profile_id)
              sendResetEmail(account.email, uuid)
              Redirect(controllers.core.routes.Admin.passwordReminderSent)
            }.getOrElse {
              val form = forgotPasswordForm.withError("email", "errors.emailNotFound")
              BadRequest(views.html.admin.forgotPassword(form,
                recaptchaKey, controllers.core.routes.Admin.forgotPasswordPost))
            }
          })
        }
      }
    }
  }

  def passwordReminderSent = Action { implicit request =>
    Ok(views.html.admin.passwordReminderSent())
  }

  private val resetPasswordForm = Form(
    tuple(
      "password" -> nonEmptyText(minLength = 6),
      "confirm" -> nonEmptyText(minLength = 6)
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (pw, pwc) => pw == pwc
    })
  )

  def resetPassword(token: String) = Action { implicit request =>
    userDAO.findByResetToken(token).map { account =>
      Ok(views.html.admin.resetPassword(resetPasswordForm,
          controllers.core.routes.Admin.resetPasswordPost(token)))
    }.getOrElse {
      Redirect(controllers.core.routes.Admin.forgotPassword)
        .flashing("error" -> "login.expiredOrInvalidResetToken")
    }
  }

  def resetPasswordPost(token: String) = Action { implicit request =>
    resetPasswordForm.bindFromRequest.fold({ errForm =>
      BadRequest(views.html.admin.resetPassword(errForm,
        controllers.core.routes.Admin.resetPasswordPost(token)))
    }, { case (pw, _) =>
      userDAO.findByResetToken(token).map { account =>
        account.updatePassword(BCrypt.hashpw(pw, BCrypt.gensalt))
        userDAO.expireToken(token)
        Redirect(controllers.core.routes.Admin.passwordLogin())
          .flashing("warning" -> "login.passwordResetNowLogin")
      }.getOrElse {
        Redirect(controllers.core.routes.Admin.forgotPassword)
          .flashing("error" -> "login.expiredOrInvalidResetToken")
      }
    })
  }

  def checkRecapture(implicit request: Request[AnyContent]): Future[Boolean] = {
    // https://developers.google.com/recaptcha/docs/verify
    val recaptchaForm = Form(
      tuple(
        "recaptcha_challenge_field" -> nonEmptyText,
        "recaptcha_response_field" -> nonEmptyText
      )
    )

    recaptchaForm.bindFromRequest.fold({ badCapture =>
      Future.successful(false)
    }, { case (challenge, response) =>
      WS.url("http://www.google.com/recaptcha/api/verify")
        .withQueryString(
        "remoteip" -> request.headers.get("REMOTE_ADDR").getOrElse(""),
        "challenge" -> challenge, "response" -> response,
        "privatekey" -> current.configuration.getString("recaptcha.key.private").getOrElse("")
      ).post("").map { response =>
        response.body.split("\n").headOption match {
          case Some("true") => true
          case Some("false") => Logger.logger.error(response.body); false
          case _ => sys.error("Unexpected captcha result: " + response.body)
        }
      }
    })
  }
}