package controllers.core.auth

import scala.collection.JavaConversions

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AccountHelpers {

  /**
   * Default group(s) new users belong to.
   */
  def defaultPortalGroups(implicit app: play.api.Application): List[String] =
    app.configuration.getStringList("ehri.portal.defaultUserGroups")
      .map(JavaConversions.collectionAsScalaIterable(_).toList)
      .getOrElse(List.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage(implicit app: play.api.Application): Boolean =
    app.configuration.getBoolean("ehri.users.messaging.default").getOrElse(false)

  def minPasswordLength(implicit app: play.api.Application): Int =
    app.configuration.getInt("ehri.passwords.minLength").getOrElse(6)
}
