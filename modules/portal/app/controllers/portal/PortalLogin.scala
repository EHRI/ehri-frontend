package controllers.portal

import play.api.mvc.{RequestHeader, Action, Controller}
import models.{UserProfileF, AccountDAO, Account}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.base.{ControllerHelpers, AuthController}
import play.api.Logger
import controllers.core.auth.oauth2.{LinkedInOauth2Provider, FacebookOauth2Provider, GoogleOAuth2Provider, Oauth2LoginHandler}
import controllers.core.auth.openid.OpenIDLoginHandler
import controllers.core.auth.userpass.UserPasswordLoginHandler
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.validation.Constraints
import play.api.Play._
import utils.forms._
import java.util.UUID
import models.sql.SqlAccount
import play.api.i18n.Messages

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalLogin extends OpenIDLoginHandler with Oauth2LoginHandler with UserPasswordLoginHandler {

  self: Controller with AuthController with LoginLogout =>

  lazy val userDAO: AccountDAO = play.api.Play.current.plugin(classOf[AccountDAO]).get

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
    Ok(views.html.p.account.signup(signupForm, controllers.portal.routes.Portal.signupPost, recaptchaKey))
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
          controllers.portal.routes.Portal.signupPost, recaptchaKey)))
      } else {
        signupForm.bindFromRequest.fold(
          errForm => immediate(BadRequest(views.html.p.account.signup(errForm,
            controllers.portal.routes.Portal.signupPost, recaptchaKey))),
          data => {
            val (name, email, pw, _) = data
            userDAO.findByEmail(email).map { _ =>
              val form = signupForm.withGlobalError("error.emailExists")
              immediate(BadRequest(views.html.p.account.signup(form,
                controllers.portal.routes.Portal.signupPost, recaptchaKey)))
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
        .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
      case Left(formError) =>
        immediate(BadRequest(views.html.openIDLogin(formError,
          action = controllers.portal.routes.Portal.openIDLoginPost)))
    }
  }

  def login = optionalUserAction { implicit maybeUser => implicit request =>
    val oauthProviders = Map(
      "facebook" -> controllers.portal.routes.Portal.facebookLogin,
      "google" -> controllers.portal.routes.Portal.googleLogin,
      "linkedin" -> controllers.portal.routes.Portal.linkedInLogin
    )

    Ok(views.html.p.account.login(openidForm, passwordLoginForm, oauthProviders))
  }

  def openIDLoginPost = openIDLoginPostAction(controllers.portal.routes.Portal.openIDCallback) { formError => implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(views.html.openIDLogin(formError, action = controllers.portal.routes.Portal.openIDLoginPost))
  }

  def passwordLoginPost = loginPostAction.async { accountOrErr => implicit request =>
    accountOrErr match {
      case Left(errorForm) =>
        immediate(BadRequest(views.html.admin.pwLogin(errorForm,
          controllers.portal.routes.Portal.passwordLoginPost)))
      case Right(account) =>
        gotoLoginSucceeded(account.id)
    }
  }
  

  def logout = optionalUserAction.async { implicit maybeUser => implicit request =>
    Logger.logger.info("Portal User '{}' logged out", maybeUser.map(_.id).getOrElse("?"))
    gotoLogoutSucceeded
  }

  def googleLogin = oauth2LoginPostAction.async(GoogleOAuth2Provider, controllers.portal.routes.Portal.googleLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
  }

  def facebookLogin = oauth2LoginPostAction.async(FacebookOauth2Provider, controllers.portal.routes.Portal.facebookLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
  }

  def linkedInLogin = oauth2LoginPostAction.async(LinkedInOauth2Provider, controllers.portal.routes.Portal.linkedInLogin) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
  }
}
