package controllers.portal

import play.api.mvc.{RequestHeader, Action, Controller}
import models.{UserProfileF, AccountDAO, Account}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.base.AuthController
import play.api.Logger
import controllers.core.auth.oauth2.{LinkedInOauth2Provider, FacebookOauth2Provider, GoogleOAuth2Provider, Oauth2LoginHandler}
import controllers.core.auth.openid.OpenIDLoginHandler
import controllers.core.auth.userpass.UserPasswordLoginHandler
import play.api.data.Form
import play.api.data.Forms._
import play.api.Play._
import utils.forms._
import java.util.UUID
import play.api.i18n.Messages

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalLogin extends OpenIDLoginHandler with Oauth2LoginHandler with UserPasswordLoginHandler {

  self: Controller with AuthController with LoginLogout =>

  lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

  private val portalRoutes = controllers.portal.routes.Portal

  val signupForm = Form(
    tuple(
      "name" -> nonEmptyText,
      "email" -> email,
      "password" -> nonEmptyText,
      "confirm" -> nonEmptyText
    ) verifying("login.passwordsDoNotMatch", f => f match {
      case (_, _, pw, pwc) => pw == pwc
    })
  )

  def signup = Action { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    Ok(views.html.p.account.signup(signupForm, portalRoutes.signupPost, recaptchaKey))
  }

  def sendValidationEmail(email: String, uuid: UUID)(implicit request: RequestHeader) {
    import com.typesafe.plugin._
    use[MailerPlugin].email
      .setSubject("Please confirm your EHRI Account Email")
      .setRecipient(email)
      .setFrom("EHRI Email Validation <noreply@ehri-project.eu>")
      .send(views.txt.p.account.mail.confirmEmail(uuid).body,
      views.html.p.account.mail.confirmEmail(uuid).body)
  }

  def signupPost = Action.async { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    checkRecapture.flatMap { ok =>
      if (!ok) {
        val form = signupForm.bindFromRequest
            .discardingErrors.withGlobalError("error.badRecaptcha")
        immediate(BadRequest(views.html.p.account.signup(form,
          portalRoutes.signupPost, recaptchaKey)))
      } else {
        signupForm.bindFromRequest.fold(
          errForm => immediate(BadRequest(views.html.p.account.signup(errForm,
            portalRoutes.signupPost, recaptchaKey))),
          data => {
            val (name, email, pw, _) = data
            userDAO.findByEmail(email).map { _ =>
              val form = signupForm.withGlobalError("error.emailExists")
              immediate(BadRequest(views.html.p.account.signup(form,
                portalRoutes.signupPost, recaptchaKey)))
            } getOrElse {

              backend.createNewUserProfile(Map(UserProfileF.NAME -> name)).flatMap { userProfile =>
                val account = userDAO.createWithPassword(userProfile.id, email.toLowerCase,
                    verified = false, staff = false, Account.hashPassword(pw))
                val uuid = UUID.randomUUID()
                account.createValidationToken(uuid)
                sendValidationEmail(email, uuid)
                gotoLoginSucceeded(userProfile.id)
              }
            }
          }
        )
      }
    }
  }

  def confirmEmail(token: String) = Action.async { implicit request =>
    userDAO.findByResetToken(token, isSignUp = true).map { account =>
      account.verify(token)
      gotoLoginSucceeded(account.id)
        .map(_.flashing("success" -> "portal.email.validated"))
    } getOrElse {
      immediate(BadRequest(
          views.html.errors.itemNotFound(Some(Messages("portal.signup.invalidSignupToken")))))
    }
  }

  def openIDCallback = openIDCallbackAction.async { formOrAccount => implicit request =>
    implicit val accountOpt: Option[Account] = None
    formOrAccount match {
      case Right(account) => gotoLoginSucceeded(account.id)
        .map(_.withSession("access_uri" -> portalRoutes.index.url))
      case Left(formError) =>
        immediate(BadRequest(views.html.openIDLogin(formError,
          action = portalRoutes.openIDLoginPost)))
    }
  }

  val oauthProviders = Map(
    "facebook" -> portalRoutes.facebookLogin,
    "google" -> portalRoutes.googleLogin,
    "linkedin" -> portalRoutes.linkedInLogin
  )

  def login = optionalUserAction { implicit maybeUser => implicit request =>
    Ok(views.html.p.account.login(openidForm, passwordLoginForm, oauthProviders))
  }

  def openIDLoginPost = openIDLoginPostAction(portalRoutes.openIDCallback) { formError => implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(views.html.openIDLogin(formError, action = portalRoutes.openIDLoginPost))
  }

  def passwordLoginPost = loginPostAction.async { accountOrErr => implicit request =>
    accountOrErr match {
      case Left(errorForm) =>
        implicit val accountOpt: Option[Account] = None
        immediate(BadRequest(views.html.p.account.login(
            openidForm, errorForm, oauthProviders)))
      case Right(account) =>
        gotoLoginSucceeded(account.id)
          .map(_.withSession("access_uri" -> portalRoutes.index.url))
    }
  }
  

  def logout = optionalUserAction.async { implicit maybeUser => implicit request =>
    Logger.logger.info("Portal User '{}' logged out", maybeUser.map(_.id).getOrElse("?"))
    gotoLogoutSucceeded
  }

  def googleLogin = oauth2LoginPostAction.async(GoogleOAuth2Provider, portalRoutes.googleLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> portalRoutes.index.url))
  }

  def facebookLogin = oauth2LoginPostAction.async(FacebookOauth2Provider, portalRoutes.facebookLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> portalRoutes.index.url))
  }

  def linkedInLogin = oauth2LoginPostAction.async(LinkedInOauth2Provider, portalRoutes.linkedInLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> portalRoutes.index.url))
  }

  /**
   * Allow a logged in user to change their password.
   * @return
   */
  def changePassword = userProfileAction { implicit user => implicit request =>
    Ok(views.html.p.account.pwChangePassword(
      changePasswordForm, controllers.portal.routes.Portal.changePasswordPost))
  }

  /**
   * Store a changed password.
   * @return
   */
  def changePasswordPost = changePasswordPostAction { boolOrErr => implicit userOpt => implicit request =>
    boolOrErr match {
      case Right(true) =>
        Redirect(globalConfig.routeRegistry.default)
          .flashing("success" -> Messages("login.passwordChanged"))
      case Right(false) =>
        Redirect(controllers.portal.routes.Portal.changePassword)
          .flashing("error" -> Messages("login.badUsernameOrPassword"))
      case Left(errForm) =>
        BadRequest(views.html.p.account.pwChangePassword(errForm,
          controllers.portal.routes.Portal.changePasswordPost))
    }
  }

  def forgotPassword = Action { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    Ok(views.html.p.account.forgotPassword(forgotPasswordForm,
      recaptchaKey, controllers.portal.routes.Portal.forgotPasswordPost))
  }

  def forgotPasswordPost = forgotPasswordPostAction { okOrErr => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    okOrErr match {
      case Right(ok) =>
        Redirect(controllers.portal.routes.Portal.passwordReminderSent)
      case Left(errForm) =>
        BadRequest(views.html.p.account.forgotPassword(errForm,
          recaptchaKey, controllers.portal.routes.Portal.forgotPasswordPost))
    }
  }

  def passwordReminderSent = Action { implicit request =>
    Ok(views.html.p.account.passwordReminderSent())
  }

  def resetPassword(token: String) = Action { implicit request =>
    userDAO.findByResetToken(token).map { account =>
      Ok(views.html.p.account.resetPassword(resetPasswordForm,
        controllers.portal.routes.Portal.resetPasswordPost(token)))
    }.getOrElse {
      Redirect(controllers.portal.routes.Portal.forgotPassword)
        .flashing("error" -> Messages("login.expiredOrInvalidResetToken"))
    }
  }

  def resetPasswordPost(token: String) = resetPasswordPostAction(token) { boolOrForm => implicit request =>
    boolOrForm match {
      case Left(errForm) =>
        BadRequest(views.html.p.account.resetPassword(errForm,
          controllers.portal.routes.Portal.resetPasswordPost(token)))
      case Right(true) =>
        Redirect(globalConfig.routeRegistry.login)
          .flashing("warning" -> "login.passwordResetNowLogin")
      case Right(false) =>
        Redirect(controllers.portal.routes.Portal.forgotPassword)
          .flashing("error" -> Messages("login.expiredOrInvalidResetToken"))
    }
  }
}
