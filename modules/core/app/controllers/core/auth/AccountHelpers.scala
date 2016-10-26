package controllers.core.auth

import scala.collection.JavaConversions


trait AccountHelpers {

  def config: play.api.Configuration

  /**
   * Default group(s) new users belong to.
   */
  def defaultPortalGroups: List[String] =
    config.getStringList("ehri.portal.defaultUserGroups")
      .map(JavaConversions.collectionAsScalaIterable(_).toList)
      .getOrElse(List.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage: Boolean =
    config.getBoolean("ehri.users.messaging.default").getOrElse(false)

  def minPasswordLength: Int =
    config.getInt("ehri.passwords.minLength").getOrElse(6)
}
