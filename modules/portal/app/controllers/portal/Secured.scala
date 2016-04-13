package controllers.portal

import controllers.base.CoreActionBuilders

trait Secured {

  self: CoreActionBuilders =>

  // This is a publicly-accessible site, but not just yet.
  override val staffOnly = config.getBoolean("ehri.portal.secured").getOrElse(true)
  override val verifiedOnly = config.getBoolean("ehri.portal.secured").getOrElse(true)
}
