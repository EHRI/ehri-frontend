package controllers.base

import controllers.portal.base.PortalController
import play.api.Play._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AdminController extends PortalController {
  override val staffOnly = true
  override val verifiedOnly = true
}