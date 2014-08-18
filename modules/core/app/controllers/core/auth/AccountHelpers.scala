package controllers.core.auth

import scala.collection.JavaConversions

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait AccountHelpers {

  /**
   * Default group(s) new users belong to.
   */
  def defaultPortalGroups: List[String] = play.api.Play.current.configuration
    .getStringList("ehri.portal.defaultUserGroups")
    .map(JavaConversions.collectionAsScalaIterable(_).toList)
    .getOrElse(List.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage: Boolean = play.api.Play.current.configuration
    .getBoolean("ehri.users.messaging.default").getOrElse(false)
}
