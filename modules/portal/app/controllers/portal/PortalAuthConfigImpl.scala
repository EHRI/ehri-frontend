package controllers.portal

import controllers.base.AuthConfigImpl
import play.api.mvc.{SimpleResult, RequestHeader, Call}
import scala.concurrent.{Future, ExecutionContext}
import play.api.Logger
import scala.concurrent.Future.{successful => immediate}
import play.api.mvc.Call
import play.api.mvc.SimpleResult

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalAuthConfigImpl extends AuthConfigImpl {

  private val portalRoutes = controllers.portal.routes.Portal

  override def defaultLoginUrl = portalRoutes.profile()
  override def defaultLogoutUrl: Call = portalRoutes.index()
  override def defaultAuthFailedUrl: Call = portalRoutes.login()

  /**
   * A redirect target after a successful user login.
   */
  override def loginSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[SimpleResult] = {
    val uri = request.session.get("access_uri").getOrElse(defaultLoginUrl.url)
    Logger.logger.debug("Redirecting logged-in user to: {}", uri)
    immediate(Redirect(uri)
      .withSession(request.session - "access_uri")
     )


  }

  /**
   * A redirect target after a successful user logout.
   */
  override def logoutSucceeded(request: RequestHeader)(implicit context: ExecutionContext): Future[SimpleResult]
  = immediate(Redirect(defaultLogoutUrl)
    .flashing("success" -> "portal.logoutSucceeded"))

}
