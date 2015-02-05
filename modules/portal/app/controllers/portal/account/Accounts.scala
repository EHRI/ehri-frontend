package controllers.portal.account

import auth.oauth2.OAuth2Flow
import auth.oauth2.providers.{LinkedInOAuth2Provider, GoogleOAuth2Provider, YahooOAuth2Provider, FacebookOAuth2Provider}
import auth.{HashedPassword, AccountManager}
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
import utils.search.{SearchItemResolver, SearchEngine}
import controllers.portal.base.PortalController

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class Accounts @Inject()(implicit globalConfig: GlobalConfig, searchDispatcher: SearchEngine, searchResolver: SearchItemResolver, backend: Backend,
                             accounts: AccountManager, mailer: MailerAPI, oAuth2Flow: OAuth2Flow)
  extends LoginLogout
  with PortalController
  with OpenIDLoginHandler
  with OAuth2LoginHandler
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

  override val oauth2Providers = Seq(GoogleOAuth2Provider, FacebookOAuth2Provider, YahooOAuth2Provider)

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

  private def doLogin(account: Account)(implicit request: RequestHeader): Future[Result] =
    accounts.setLoggedIn(account).flatMap(_ => gotoLoginSucceeded(account.id))

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
    implicit val userOpt: Option[UserProfile] = None
    def badForm(form: Form[SignupData], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.p.account.login(
          passwordLoginForm,
          form,
          accountRoutes.signupPost(),
          recaptchaKey,
          openidForm,
          oauth2Providers,
          isLogin = false)
      )
    }

    val boundForm: Form[SignupData] = SignupData.form.bindFromRequest

    checkRecapture.flatMap {
      case false =>
        badForm(boundForm.withGlobalError("error.badRecaptcha"))
      case true if !checkRateLimit(request) =>
        badForm(boundForm.withGlobalError(rateLimitError), TooManyRequest)
      case true =>
        boundForm.fold(
          errForm => badForm(errForm),
          data => {
            accounts.findByEmail(data.email.toLowerCase).flatMap {
              // that email already exists, problem!
              case Some(_) =>
                badForm(boundForm.withError(SignupData.EMAIL, "error.emailExists"))
              // we're okay to proceed...
              case None =>
                implicit val apiUser = AnonymousUser
                val uuid = UUID.randomUUID()
                val profileData = Map(UserProfileF.NAME -> data.name)
                for {
                  profile <- backend.createNewUserProfile[UserProfile](
                    data = profileData, groups = defaultPortalGroups)
                  account <- accounts.create(Account(
                    id = profile.id,
                    email = data.email.toLowerCase,
                    verified = false,
                    active = true,
                    staff = false,
                    allowMessaging = data.allowMessaging,
                    password = Some(HashedPassword.fromPlain(data.password))
                  ))
                  _ <- accounts.createToken(account.id, uuid, isSignUp = true)
                  result <- doLogin(account)
                    .map(_.flashing("success" -> "signup.confirmation"))
                } yield {
                  sendValidationEmail(data.email, uuid)
                  result
                }
            }
          }
        )
    }
  }

  def confirmEmail(token: String) = NotReadOnlyAction.async { implicit request =>
    accounts.findByToken(token, isSignUp = true).flatMap {
      case Some(account) => for {
        _ <- accounts.verify(account, token)
        result <- doLogin(account)
          .map(_.flashing("success" -> "signup.validation.confirmation"))
      } yield result
      case None =>
      immediate(BadRequest(
          views.html.errors.itemNotFound(Some(Messages("signup.validation.badToken")))))
    }
  }

  def openIDCallback = OpenIdCallbackAction.async { implicit request =>
    implicit val userOpt: Option[UserProfile] = None
    implicit val accountOpt: Option[Account] = None
    request.formOrAccount match {
      case Right(account) => doLogin(account)
      case Left(formError) => immediate(
        BadRequest(
          views.html.p.account.login(
            passwordLoginForm,
            SignupData.form,
            accountRoutes.signupPost(),
            recaptchaKey,
            formError,
            oauth2Providers)
          )
        )
    }
  }

  def signup = loginOrSignup(isLogin = false)

  def loginOrSignup(isLogin: Boolean) = OptionalAuthAction { implicit authRequest =>
    if (globalConfig.readOnly) {
      Redirect(portalRoutes.index()).flashing("warning" -> "login.disabled")
    } else {
      implicit val userOpt: Option[UserProfile] = None
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
            oauth2Providers,
            isLogin = isLogin)
          )
      }
    }
  }

  def openIDLoginPost(isLogin: Boolean = true) = OpenIdLoginAction(accountRoutes.openIDCallback()) { implicit request =>
    implicit val userOpt: Option[UserProfile] = None
    implicit val accountOpt: Option[Account] = None
    BadRequest(
      views.html.p.account.login(
        passwordLoginForm,
        SignupData.form,
        accountRoutes.signupPost(),
        recaptchaKey,
        request.errorForm,
        oauth2Providers,
        isLogin
      )
    )
  }

  def passwordLoginPost = (NotReadOnlyAction andThen UserPasswordLoginAction).async { implicit request =>
    implicit val userOpt: Option[UserProfile] = None

    def badForm(f: Form[(String,String)], status: Status = BadRequest): Future[Result] = immediate {
      status(
        views.html.p.account.login(
          f,
          SignupData.form,
          accountRoutes.signupPost(),
          recaptchaKey,
          openidForm,
          oauth2Providers,
          isLogin = true
        )
      )
    }

    val boundForm: Form[(String, String)] = passwordLoginForm.bindFromRequest
    if (!checkRateLimit(request)) {
      badForm(boundForm.withGlobalError(rateLimitError), TooManyRequest)
    } else request.formOrAccount match {
      case Left(errorForm) => badForm(errorForm)
      case Right(account) => doLogin(account)
    }
  }

  def logout = Action.async { implicit authRequest =>
    gotoLogoutSucceeded
  }

  def oauth2(provider: String, code: Option[String], state: Option[String]) =
    OAuth2LoginAction(provider, code, state, accountRoutes.oauth2(provider)).async { implicit request =>
      request.accountOrErr match {
        case Left(error) => immediate(Redirect(accountRoutes.loginOrSignup())
          .flashing("danger" -> error))
        case Right(account) => doLogin(account)
      }
    }

  def forgotPassword = OptionalUserAction { implicit request =>
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

  def resetPassword(token: String) = OptionalUserAction.async { implicit request =>
    accounts.findByToken(token).map {
      case Some(account) => Ok(views.html.p.account.resetPassword(resetPasswordForm,
        accountRoutes.resetPasswordPost(token)))
      case _ => Redirect(accountRoutes.forgotPassword())
        .flashing("danger" -> "login.error.badResetToken")
    }
  }

  def resendVerificationPost() = WithUserAction.async { implicit request =>
    request.user.account match {
      case Some(account) =>
        val uuid = UUID.randomUUID()
        accounts.createToken(account.id, uuid, isSignUp = true).map { _ =>
          sendValidationEmail(account.email, uuid)
          val redirect = request.headers.get(HttpHeaders.REFERER)
            .getOrElse(portalRoutes.index().url)
          Redirect(redirect)
            .flashing("success" -> "mail.emailConfirmationResent")
        }
      case _ => authorizationFailed(request)
    }
  }

  def resetPasswordPost(token: String) = ResetPasswordAction(token).async { implicit request =>
    request.formOrAccount match {
      case Left(errForm) => immediate(BadRequest(views.html.p.account.resetPassword(errForm,
        accountRoutes.resetPasswordPost(token))))
      case Right(account) => doLogin(account)
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
