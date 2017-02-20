package controllers.core.auth

trait AccountHelpers {

  protected def config: play.api.Configuration


  /**
   * Default group(s) new users belong to.
   */
  import scala.collection.JavaConverters._
  def defaultPortalGroups: Seq[String] =
    config.getStringList("ehri.portal.defaultUserGroups")
      .map(_.asScala).getOrElse(Seq.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage: Boolean =
    config.getBoolean("ehri.users.messaging.default").getOrElse(false)

  def minPasswordLength: Int =
    config.getInt("ehri.passwords.minLength").getOrElse(6)
}
