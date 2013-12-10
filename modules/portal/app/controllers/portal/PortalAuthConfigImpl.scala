package controllers.portal

import controllers.base.AuthConfigImpl
import play.api.mvc.Call

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait PortalAuthConfigImpl extends AuthConfigImpl {

  private val portalRoutes = controllers.portal.routes.Portal

  override def defaultLoginUrl = portalRoutes.profile
  override def defaultLogoutUrl: Call = portalRoutes.index
  override def defaultAuthFailedUrl: Call = portalRoutes.openIDLogin
}
