package controllers.portal.account

import play.api.mvc._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.base.SessionPreferences
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
import backend.{AnonymousUser, Backend, ApiUser}
import play.api.mvc.Result
import com.typesafe.plugin.MailerAPI
import views.html.p
import com.google.inject.{Singleton, Inject}
import utils.search.{Resolver, Dispatcher}
import controllers.portal.Secured
import play.api.libs.json.Json
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Accounts @Inject()(implicit globalConfig: GlobalConfig, searchDispatcher: Dispatcher, searchResolver: Resolver, backend: Backend,
                             userDAO: AccountDAO, mailer: MailerAPI)
  extends LoginLogout
  with PortalController
  with OpenIDLoginHandler
  with Oauth2LoginHandler
  with UserPasswordLoginHandler
  with AccountHelpers {

  private val portalRoutes = controllers.portal.routes.Portal
  private val accountRoutes = controllers.portal.account.routes.Accounts

  private def recaptchaKey = current.configuration.getString("recaptcha.key.public")
    .getOrElse("fakekey")

  def account = OptionalUserAction { implicit request =>
    Ok(Json.toJson(request.userOpt.flatMap(_.account)))
  }

  /**
   * Prevent people signin up, logging in etc when in read-only mode.
   */
  case class NotReadOnly[A](action: Action[A]) extends Action[A] {
    def apply(request: Request[A]): Future[Result] = {
      if (globalConfig.readOnly) {
        Future.successful(Redirect(portalRoutes.index())
          .flashing("warning" -> Messages("login.disabled")))
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
              implicit val apiUser = AnonymousUser
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
                  r.flashing("success" -> "signup.confirmation"))
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
        .map(_.flashing("success" -> "signup.validation.confirmation"))
    } getOrElse {
      immediate(BadRequest(
          views.html.errors.itemNotFound(Some(Messages("signup.validation.badToken")))))
    }
  }

  def openIDCallback = OpenIdCallbackAction.async { implicit request =>
    implicit val accountOpt: Option[Account] = None
    request.formOrAccount match {
      case Right(account) => gotoLoginSucceeded(account.id)
      case Left(formError) => immediate(
        BadRequest(
          views.html.p.account.login(
            passwordLoginForm,
            SignupData.form,
            accountRoutes.signupPost(),
            recaptchaKey,
            formError,
            oauthProviders)
          )
        )
    }
  }

  val oauthProviders = Map(
    "facebook" -> accountRoutes.facebookLogin,
    "google" -> accountRoutes.googleLogin
  )

  def login = OptionalAuthAction { implicit authRequest =>
    if (globalConfig.readOnly) {
      Redirect(portalRoutes.index()).flashing("warning" -> Messages("login.disabled"))
    } else {
      implicit val accountOpt = authRequest.user
      accountOpt match {
        case Some(user) => Redirect(portalRoutes.index())
          .flashing("warning" -> Messages("login.alreadyLoggedIn", user.email))
        case None =>
          Ok(views.html.p.account.login(
            passwordLoginForm,
            SignupData.form,
            accountRoutes.signupPost(),
            recaptchaKey,
            openidForm,
            oauthProviders)
          )
      }
    }
  }

  def openIDLoginPost(isLogin: Boolean = true) = OpenIdLoginAction(accountRoutes.openIDCallback) { implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(
      views.html.p.account.login(
        passwordLoginForm,
        SignupData.form,
        accountRoutes.signupPost(),
        recaptchaKey,
        request.errorForm,
        oauthProviders
      )
    )
  }

  def passwordLoginPost = UserPasswordLoginAction.async { implicit request =>
    request.formOrAccount match {
      case Left(errorForm) =>
        implicit val accountOpt: Option[Account] = None
        immediate(
          BadRequest(
            views.html.p.account.login(
              errorForm,
              SignupData.form,
              accountRoutes.signupPost(),
              recaptchaKey,
              openidForm,
              oauthProviders
            )
          )
        )
      case Right(account) => gotoLoginSucceeded(account.id)
    }
  }

  def logout = Action.async { implicit authRequest =>
    gotoLogoutSucceeded
  }

  def googleLogin = OAuth2LoginAction(GoogleOAuth2Provider, accountRoutes.googleLogin()).async { implicit request =>
    gotoLoginSucceeded(request.user.id)
  }

  def facebookLogin = OAuth2LoginAction(FacebookOauth2Provider, accountRoutes.facebookLogin()).async { implicit request =>
    gotoLoginSucceeded(request.user.id)
  }

  def linkedInLogin = OAuth2LoginAction(LinkedInOauth2Provider, accountRoutes.linkedInLogin()).async {implicit request =>
    gotoLoginSucceeded(request.user.id)
  }

  def forgotPassword = Action { implicit request =>
    val recaptchaKey = current.configuration.getString("recaptcha.key.public")
      .getOrElse("fakekey")
    Ok(views.html.p.account.forgotPassword(forgotPasswordForm,
      recaptchaKey, accountRoutes.forgotPasswordPost()))
  }

  def forgotPasswordPost = ForgotPasswordAction { implicit request =>
    request.formOrAccount match {
      case Right((account,uuid)) =>
        sendResetEmail(account.email, uuid)
        Redirect(portalRoutes.index())
          .flashing("warning" -> "login.password.reset.sentLink")
      case Left(errForm) =>
        BadRequest(views.html.p.account.forgotPassword(errForm,
          recaptchaKey, accountRoutes.forgotPasswordPost()))
    }
  }

  def passwordReminderSent = Action { implicit request =>
    Ok(views.html.p.account.passwordReminderSent())
  }

  def changePassword = WithUserAction { implicit request =>
    Ok(views.html.p.account.changePassword(request.user.account.get, changePasswordForm,
      accountRoutes.changePasswordPost()))
  }

  /**
   * Store a changed password.
   */
  def changePasswordPost = ChangePasswordAction { implicit request =>
    implicit val userOpt = Some(request.user)
    val account = request.user.account.get
    request.errForm.fold(
      Redirect(defaultLoginUrl)
        .flashing("success" -> "login.password.change.confirmation")
    )(errForm => BadRequest(p.account.changePassword(
      account, errForm, accountRoutes.changePasswordPost())))
  }

  def resetPassword(token: String) = Action { implicit request =>
    userDAO.findByResetToken(token).fold(
      ifEmpty = Redirect(accountRoutes.forgotPassword())
        .flashing("error" -> "login.error.badResetToken")
    )(
      account =>
        Ok(views.html.p.account.resetPassword(resetPasswordForm,
          accountRoutes.resetPasswordPost(token)))
      )
  }

  def resendVerificationPost() = WithUserAction { implicit request =>
    request.user.account.map { account =>
      val uuid = UUID.randomUUID()
      account.createValidationToken(uuid)
      sendValidationEmail(account.email, uuid)
      val redirect = request.headers.get(HttpHeaders.REFERER)
        .getOrElse(portalRoutes.index().url)
      Redirect(redirect)
        .flashing("success" -> "mail.emailConfirmationResent")
    }.getOrElse(Unauthorized)
  }

  def resetPasswordPost(token: String) = ResetPasswordAction(token).async { implicit request =>
    request.formOrAccount match {
      case Left(errForm) => immediate(BadRequest(views.html.p.account.resetPassword(errForm,
        accountRoutes.resetPasswordPost(token))))
      case Right(account) => gotoLoginSucceeded(account.id)
        .map(_.flashing("success" -> "login.password.reset.confirmation"))
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
