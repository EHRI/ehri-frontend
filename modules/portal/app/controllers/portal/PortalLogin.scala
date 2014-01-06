package controllers.portal

import play.api.mvc.Controller
import models.Account
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future.{successful => immediate}
import jp.t2v.lab.play2.auth.LoginLogout
import controllers.base.{ControllerHelpers, AuthController}
import controllers.core.{OpenIDLoginHandler}
import play.api.Logger
import controllers.core.oauth2.{FacebookOauth2Provider, GoogleOAuth2Provider, Oauth2LoginHandler}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalLogin extends OpenIDLoginHandler with Oauth2LoginHandler {

  self: Controller with AuthController with LoginLogout =>

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

  def openIDLogin = optionalUserAction { implicit maybeUser => implicit request =>
    Ok(views.html.openIDLogin(openidForm, action = controllers.portal.routes.Portal.openIDLoginPost))
  }

  def openIDLoginPost = openIDLoginPostAction(controllers.portal.routes.Portal.openIDCallback) { formError => implicit request =>
    implicit val accountOpt: Option[Account] = None
    BadRequest(views.html.openIDLogin(formError, action = controllers.portal.routes.Portal.openIDLoginPost))
  }

  def logout = optionalUserAction.async { implicit maybeUser => implicit request =>
    Logger.logger.info("Portal User '{}' logged out", maybeUser.map(_.id).getOrElse("?"))
    gotoLogoutSucceeded
  }

  def googleLogin = optionalUserAction { implicit maybeUser => implicit request =>
    Ok(views.html.p.oauth2Login(action = controllers.portal.routes.Portal.googleLoginPost))
  }

  def googleLoginPost = oauth2LoginPostAction.async(GoogleOAuth2Provider, controllers.portal.routes.Portal.googleLoginPost) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
  }

  def facebookLogin = optionalUserAction { implicit maybeUser => implicit request =>
    Ok(views.html.p.oauth2Login(action = controllers.portal.routes.Portal.facebookLoginPost))
  }

  def facebookLoginPost = oauth2LoginPostAction.async(FacebookOauth2Provider, controllers.portal.routes.Portal.facebookLoginPost) { account => implicit request =>
    gotoLoginSucceeded(account.id)
      .map(_.withSession("access_uri" -> controllers.portal.routes.Portal.index.url))
  }
}
