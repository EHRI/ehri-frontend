package controllers.portal.account

import play.api.data.Form
import play.api.mvc._
import models._
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.core.auth.oauth2._
import controllers.core.auth.openid.OpenIDLoginHandler
import controllers.core.auth.userpass.UserPasswordLoginHandler
import global.GlobalConfig
import play.api.Play._
import utils.forms._
import java.util.UUID
import play.api.i18n.Messages
import com.google.common.net.HttpHeaders
import controllers.core.auth.AccountHelpers
import scala.concurrent.Future
import backend.{AnonymousUser, Backend}
import play.api.mvc.Result
import com.typesafe.plugin.MailerAPI
import views.html.p
import com.google.inject.{Singleton, Inject}
import utils.search.{Resolver, Dispatcher}
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

  private def rateLimitTimeoutSecs = current.configuration.getInt("ehri.ratelimit.timeout")
    .getOrElse(3600)

  private def rateLimitError(implicit r: RequestHeader) =
    Messages("error.rateLimit", rateLimitTimeoutSecs / 60)

  val oauthProviders = Map(
    FacebookOauth2Provider.name -> accountRoutes.facebookLogin,
    GoogleOAuth2Provider.name -> accountRoutes.googleLogin,
    YahooOAuth2Provider.name -> accountRoutes.yahooLogin
  )

  /**
   * Prevent people signin up, logging in etc when in read-only mode.
   */
  case class NotReadOnly[A](action: Action[A]) extends Action[A] {
    def apply(request: Request[A]): Future[Result] = {
      if (globalConfig.readOnly) {
        Future.successful(Redirect(portalRoutes.index())
          .flashing("warning" -> "login.disabled"))
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

  def sendValidationEmail(email: String, uuid: UUID)(implicit request: RequestHeader) {
    mailer
      .setSubject("Please confirm your EHRI Account Email")
      .setRecipient(email)
      .setFrom("EHRI Email Validation <noreply@ehri-project.eu>")
      .send(views.txt.p.account.mail.confirmEmail(uuid).body,
      views.html.p.account.mail.confirmEmail(uuid).body)
  }

  def RateLimit = new ActionBuilder[Request] {
    override def invokeBlock[A](request: Request[A], block: (Request[A]) => Future[Result]): Future[Result] = {
      if (request.method != "POST") block(request)
      else {
        if (checkRateLimit(request)) block(request)
        else immediate(TooManyRequest(rateLimitError(request)))
      }
    }
  }

  def signupPost = NotReadOnlyAction.async { implicit request =>
    def badForm(form: Form[SignupData], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.p.account.login(
          passwordLoginForm,
          form,
          accountRoutes.signupPost(),
          recaptchaKey,
          openidForm,
          oauthProviders,
          isLogin = false)
      )
    }

    checkRecapture.flatMap { ok =>
      val boundForm: Form[SignupData] = SignupData.form.bindFromRequest
      if (!ok) {
        badForm(boundForm.withGlobalError("error.badRecaptcha"))
      } else if (!checkRateLimit(request)) {
        badForm(boundForm.withGlobalError(rateLimitError), TooManyRequest)
      } else {
        boundForm.fold(
          errForm => badForm(errForm),
          data => {
            userDAO.findByEmail(data.email).map { _ =>
              badForm(boundForm.withError(SignupData.EMAIL, "error.emailExists"))
            } getOrElse {
              implicit val apiUser = AnonymousUser
              backend.createNewUserProfile[UserProfile](
                  data = Map(UserProfileF.NAME -> data.name), groups = defaultPortalGroups)
                  .flatMap { userProfile =>
                val account = userDAO.createWithPassword(userProfile.id, data.email.toLowerCase,
                    verified = false, staff = false, allowMessaging = data.allowMessaging,
                  userDAO.hashPassword(data.password))
                val uuid = UUID.randomUUID()
                account.createValidationToken(uuid)
                sendValidationEmail(data.email, uuid)

                gotoLoginSucceeded(userProfile.id)
                  .map(_.flashing("success" -> "signup.confirmation"))
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

  def signup = loginOrSignup(isLogin = false)

  def loginOrSignup(isLogin: Boolean) = OptionalAuthAction { implicit authRequest =>
    if (globalConfig.readOnly) {
      Redirect(portalRoutes.index()).flashing("warning" -> "login.disabled")
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
            oauthProviders,
            isLogin = isLogin)
          )
      }
    }
  }

  def openIDLoginPost(isLogin: Boolean = true) = OpenIdLoginAction(accountRoutes.openIDCallback()) { implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(
      views.html.p.account.login(
        passwordLoginForm,
        SignupData.form,
        accountRoutes.signupPost(),
        recaptchaKey,
        request.errorForm,
        oauthProviders,
        isLogin
      )
    )
  }

  def passwordLoginPost = (NotReadOnlyAction andThen UserPasswordLoginAction).async { implicit request =>

    def badForm(f: Form[(String,String)], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.p.account.login(
          f,
          SignupData.form,
          accountRoutes.signupPost(),
          recaptchaKey,
          openidForm,
          oauthProviders,
          isLogin = true
        )
      )
    }

    val boundForm: Form[(String, String)] = passwordLoginForm.bindFromRequest
    if (!checkRateLimit(request)) {
      badForm(boundForm.withGlobalError(rateLimitError), TooManyRequest)
    } else request.formOrAccount match {
      case Left(errorForm) => badForm(errorForm)
      case Right(account) => gotoLoginSucceeded(account.id)
    }
  }

  def logout = Action.async { implicit authRequest =>
    gotoLogoutSucceeded
  }

  private def handleOAuth2Login[A](implicit request: OAuth2Request[A]): Future[Result] = {
    request.accountOrErr match {
      case Left(error) => immediate(Redirect(accountRoutes.loginOrSignup())
        .flashing("danger" -> error))
      case Right(account) => gotoLoginSucceeded(account.id)
    }
  }

  def googleLogin = OAuth2LoginAction(GoogleOAuth2Provider, accountRoutes.googleLogin()).async { implicit request =>
    handleOAuth2Login(request)
  }

  def facebookLogin = OAuth2LoginAction(FacebookOauth2Provider, accountRoutes.facebookLogin()).async { implicit request =>
    handleOAuth2Login(request)
  }

  def yahooLogin = OAuth2LoginAction(YahooOAuth2Provider, accountRoutes.yahooLogin()).async { implicit request =>
    handleOAuth2Login(request)
  }

  def linkedInLogin = OAuth2LoginAction(LinkedInOauth2Provider, accountRoutes.linkedInLogin()).async {implicit request =>
    handleOAuth2Login(request)
  }

  def forgotPassword = Action { implicit request =>
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
