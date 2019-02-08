package controllers.base

import controllers.portal.base.PortalController

trait AdminController extends PortalController {
  override val staffOnly = true
  override val verifiedOnly = true
}