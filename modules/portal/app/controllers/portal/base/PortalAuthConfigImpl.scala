package controllers.portal.base

import controllers.base.{CoreActionBuilders, AuthConfigImpl}
import play.api.Logger
import play.api.mvc.{Call, RequestHeader, Result}

import scala.concurrent.Future.{successful => immediate}
import scala.concurrent.{ExecutionContext, Future}

trait PortalAuthConfigImpl extends AuthConfigImpl {

  this: CoreActionBuilders =>

  override def defaultLogoutUrl: Call = controllers.portal.routes.Portal.index()
  override def defaultLoginUrl = controllers.portal.users.routes.UserProfiles.profile()

  /**
   * A redirect target after a successful user login.
   */
  override def loginSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] = {
    val uri = request.session.get(ACCESS_URI).getOrElse(defaultLoginUrl.url)
    Logger.logger.debug("Redirecting logged-in user to: {}", uri)
    immediate(Redirect(uri).withSession(request.session - ACCESS_URI))
  }

  /**
   * A redirect target after a successful user logout.
   */
  override def logoutSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[Result] =
    immediate(Redirect(defaultLogoutUrl)
      .flashing("success" -> "logout.confirmation"))
}
