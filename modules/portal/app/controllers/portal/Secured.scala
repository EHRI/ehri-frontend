package controllers.portal

import controllers.base.CoreActionBuilders

trait Secured {

  self: CoreActionBuilders =>

  // This is a publically-accessible site, but not just yet.
  override val staffOnly = app.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = app.configuration.getBoolean("ehri.portal.secured").getOrElse(true)
}
