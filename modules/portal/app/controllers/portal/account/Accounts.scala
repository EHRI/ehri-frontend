package controllers.portal.account

import play.api.mvc._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.base.{ControllerHelpers, SessionPreferences, AuthController}
import play.api.Logger
import controllers.core.auth.oauth2.{LinkedInOauth2Provider, FacebookOauth2Provider, GoogleOAuth2Provider, Oauth2LoginHandler}
import controllers.core.auth.openid.OpenIDLoginHandler
import controllers.core.auth.userpass.UserPasswordLoginHandler
import global.GlobalConfig
import play.api.Play._
import utils.forms._
import java.util.UUID
import play.api.i18n.Messages
import utils.SessionPrefs
import com.google.common.net.HttpHeaders
import controllers.core.auth.AccountHelpers
import scala.concurrent.Future
import backend.{Backend, ApiUser}
import play.api.mvc.Result
import com.typesafe.plugin.MailerAPI
import views.html.p
import com.google.inject.Inject
import utils.search.{Resolver, Dispatcher}
import controllers.portal.{Secured}
import play.api.libs.json.Json
import controllers.portal.base.{PortalController, PortalAuthConfigImpl}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class Accounts @Inject()(implicit globalConfig: GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                             userDAO: AccountDAO, mailer: MailerAPI)
  extends LoginLogout
  with PortalController
  with OpenIDLoginHandler
  with Oauth2LoginHandler
  with UserPasswordLoginHandler
  with AccountHelpers
  with SessionPreferences[SessionPrefs]
  with Secured {

  private val portalRoutes = controllers.portal.routes.Portal
  private val accountRoutes = controllers.portal.account.routes.Accounts

  val defaultPreferences = new SessionPrefs

  def account = userProfileAction { implicit userOpt => implicit request =>
    Ok(Json.toJson(userOpt.flatMap(_.account)))
  }

  /**
   * Prevent people signin up, logging in etc when in read-only mode.
   */
  case class NotReadOnly[A](action: Action[A]) extends Action[A] {
    def apply(request: Request[A]): Future[Result] = {
      if (globalConfig.readOnly) {
        Future.successful(Redirect(portalRoutes.index())
          .flashing("warning" -> Messages("portal.login.disabled")))
      } else action(request)
    }
    lazy val parser = action.parser
  }

  object NotReadOnlyAction extends ActionBuilder[Request] {
    def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]) = {
      block(request)
    }
    override def composeAction[A](action: Action[A]) = new NotReadOnly(action)
  }

  def signup = NotReadOnlyAction { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    Ok(views.html.p.account.signup(SignupData.form, accountRoutes.signupPost(), recaptchaKey))
  }

  def sendValidationEmail(email: String, uuid: UUID)(implicit request: RequestHeader) {
    mailer
      .setSubject("Please confirm your EHRI Account Email")
      .setRecipient(email)
      .setFrom("EHRI Email Validation <noreply@ehri-project.eu>")
      .send(views.txt.p.account.mail.confirmEmail(uuid).body,
      views.html.p.account.mail.confirmEmail(uuid).body)
  }

  def signupPost = NotReadOnlyAction.async { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")

    checkRecapture.flatMap { ok =>
      if (!ok) {
        val form = SignupData.form.bindFromRequest
            .discardingErrors.withGlobalError("error.badRecaptcha")
        immediate(BadRequest(views.html.p.account.signup(form,
          accountRoutes.signupPost(), recaptchaKey)))
      } else {
        SignupData.form.bindFromRequest.fold(
          errForm => immediate(BadRequest(views.html.p.account.signup(errForm,
            accountRoutes.signupPost(), recaptchaKey))),
          data => {
            userDAO.findByEmail(data.email).map { _ =>
              val form = SignupData.form.withGlobalError("error.emailExists")
              immediate(BadRequest(views.html.p.account.signup(form,
                accountRoutes.signupPost(), recaptchaKey)))
            } getOrElse {
              implicit val apiUser = ApiUser()
              backend.createNewUserProfile[UserProfile](
                  data = Map(UserProfileF.NAME -> data.name), groups = defaultPortalGroups)
                  .flatMap { userProfile =>
                val account = userDAO.createWithPassword(userProfile.id, data.email.toLowerCase,
                    verified = false, staff = false, allowMessaging = data.allowMessaging,
                  Account.hashPassword(data.password))
                val uuid = UUID.randomUUID()
                account.createValidationToken(uuid)
                sendValidationEmail(data.email, uuid)

                gotoLoginSucceeded(userProfile.id).map(r =>
                  r.flashing("success" -> "portal.signup.confirmation"))
              }
            }
          }
        )
      }
    }
  }

  def confirmEmail(token: String) = NotReadOnlyAction.async { implicit request =>
    userDAO.findByResetToken(token, isSignUp = true).map { account =>
      account.verify(token)
      gotoLoginSucceeded(account.id)
        .map(_.flashing("success" -> "portal.signup.validation.confirmation"))
    } getOrElse {
      immediate(BadRequest(
          views.html.errors.itemNotFound(Some(Messages("portal.signup.invalidSignupToken")))))
    }
  }

  def openIDCallback = openIDCallbackAction.async { formOrAccount => implicit request =>
    implicit val accountOpt: Option[Account] = None
    formOrAccount match {
      case Right(account) => gotoLoginSucceeded(account.id)
        .map(_.withSession("access_uri" -> portalRoutes.index().url))
      case Left(formError) =>
        immediate(BadRequest(
          views.html.p.account.login(formError, passwordLoginForm, oauthProviders)))
    }
  }

  val oauthProviders = Map(
    "facebook" -> accountRoutes.facebookLogin,
    "google" -> accountRoutes.googleLogin
  )

  def login = optionalUserAction { implicit accountOpt => implicit request =>
    if (globalConfig.readOnly) {
      Redirect(portalRoutes.index()).flashing("warning" -> Messages("portal.login.disabled"))
    } else {
      accountOpt match {
        case Some(user) => Redirect(portalRoutes.index())
          .flashing("warning" -> Messages("portal.login.alreadyLoggedIn", user.email))
        case None => Ok(views.html.p.account.login(openidForm, passwordLoginForm, oauthProviders))
      }
    }
  }

  def openIDLoginPost = openIDLoginPostAction(accountRoutes.openIDCallback()) { formError => implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(
      views.html.p.account.login(formError, passwordLoginForm, oauthProviders))
  }

  def passwordLoginPost = loginPostAction.async { accountOrErr => implicit request =>
    accountOrErr match {
      case Left(errorForm) =>
        implicit val accountOpt: Option[Account] = None
        immediate(BadRequest(views.html.p.account.login(
            openidForm, errorForm, oauthProviders)))
      case Right(account) => gotoLoginSucceeded(account.id)
    }
  }

  def logout = optionalUserAction.async { implicit maybeUser => implicit request =>
    Logger.logger.info("Portal User '{}' logged out", maybeUser.map(_.id).getOrElse("?"))
    gotoLogoutSucceeded
  }

  def googleLogin = oauth2LoginPostAction.async(GoogleOAuth2Provider, accountRoutes.googleLogin()) { account => implicit request =>
    gotoLoginSucceeded(account.id)
  }

  def facebookLogin = oauth2LoginPostAction.async(FacebookOauth2Provider, accountRoutes.facebookLogin()) { account => implicit request =>
    gotoLoginSucceeded(account.id)
  }

  def linkedInLogin = oauth2LoginPostAction.async(LinkedInOauth2Provider, accountRoutes.linkedInLogin()) { account => implicit request =>
    gotoLoginSucceeded(account.id)
  }

  def forgotPassword = Action { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    Ok(views.html.p.account.forgotPassword(forgotPasswordForm,
      recaptchaKey, accountRoutes.forgotPasswordPost()))
  }

  def forgotPasswordPost = forgotPasswordPostAction { uuidOrErr => implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    uuidOrErr match {
      case Right((account,uuid)) =>
        sendResetEmail(account.email, uuid)
        Redirect(portalRoutes.index())
          .flashing("warning" -> "login.sentPasswordResetLink")
      case Left(errForm) =>
        BadRequest(views.html.p.account.forgotPassword(errForm,
          recaptchaKey, accountRoutes.forgotPasswordPost()))
    }
  }

  def passwordReminderSent = Action { implicit request =>
    Ok(views.html.p.account.passwordReminderSent())
  }

  def changePassword = withUserAction { implicit user => implicit request =>
    user.account.map { account =>
      Ok(views.html.p.account.changePassword(account, changePasswordForm,
        accountRoutes.changePasswordPost()))
    }.getOrElse {
      Redirect(accountRoutes.changePassword())
        .flashing("error" -> Messages("login.expiredOrInvalidResetToken"))
    }
  }

  /**
   * Store a changed password.
   */
  def changePasswordPost = changePasswordPostAction { boolOrErr => implicit user => implicit request =>
    assert(user.account.isDefined, "User account is not present!")
    val account = user.account.get
    boolOrErr match {
      case Right(true) =>
        Redirect(defaultLoginUrl)
          .flashing("success" -> Messages("login.passwordChanged"))
      case Right(false) =>
        BadRequest(p.account.changePassword(
          account, changePasswordForm
            .withGlobalError("login.badUsernameOrPassword"), accountRoutes.changePassword()))
      case Left(errForm) =>
        BadRequest(p.account.changePassword(
          account, errForm, accountRoutes.changePassword()))
    }
  }

  def resetPassword(token: String) = Action { implicit request =>
    userDAO.findByResetToken(token).map { account =>
      Ok(views.html.p.account.resetPassword(resetPasswordForm,
        accountRoutes.resetPasswordPost(token)))
    }.getOrElse {
      Redirect(accountRoutes.forgotPassword())
        .flashing("error" -> Messages("login.expiredOrInvalidResetToken"))
    }
  }

  def resendVerificationPost() = withUserAction { implicit user => implicit request =>
    user.account.map { account =>
      val uuid = UUID.randomUUID()
      account.createValidationToken(uuid)
      sendValidationEmail(account.email, uuid)
      val redirect = request.headers.get(HttpHeaders.REFERER)
        .getOrElse(portalRoutes.index().url)
      Redirect(redirect)
        .flashing("success" -> Messages("portal.mail.emailConfirmationResent"))
    }.getOrElse(Unauthorized)
  }

  def resetPasswordPost(token: String) = resetPasswordPostAction(token) { boolOrForm => implicit request =>
    boolOrForm match {
      case Left(errForm) =>
        BadRequest(views.html.p.account.resetPassword(errForm,
          accountRoutes.resetPasswordPost(token)))
      case Right(true) =>
        Redirect(accountRoutes.login())
          .flashing("warning" -> "login.passwordResetNowLogin")
      case Right(false) =>
        Redirect(accountRoutes.forgotPassword())
          .flashing("error" -> Messages("login.expiredOrInvalidResetToken"))
    }
  }

  private def sendResetEmail(email: String, uuid: UUID)(implicit request: RequestHeader) {
    mailer
      .setSubject("EHRI Password Reset")
      .setRecipient(email)
      .setFrom("EHRI Password Reset <noreply@ehri-project.eu>")
      .send(views.txt.p.account.mail.forgotPassword(uuid).body,
      views.html.p.account.mail.forgotPassword(uuid).body)
  }
}
