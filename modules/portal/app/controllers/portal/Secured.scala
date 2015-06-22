package controllers.portal

import controllers.base.CoreActionBuilders

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Secured {

  self: CoreActionBuilders =>

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = app.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = app.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
}
