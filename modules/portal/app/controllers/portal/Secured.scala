package controllers.portal

import controllers.base.AuthController
import play.api.Play._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Secured {

  self: AuthController =>

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = current.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
}
