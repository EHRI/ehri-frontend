package controllers.core.auth

trait AccountHelpers {

  protected def config: play.api.Configuration

  /**
   * Default group(s) new users belong to.
   */
  def defaultPortalGroups: Seq[String] = config
      .getOptional[Seq[String]]("ehri.portal.defaultUserGroups")
      .getOrElse(Seq.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage: Boolean = config
    .getOptional[Boolean]("ehri.users.messaging.default")
    .getOrElse(false)

  def minPasswordLength: Int = config
    .getOptional[Int]("ehri.passwords.minLength")
    .getOrElse(6)
}
