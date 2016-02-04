package controllers.core.auth

import scala.collection.JavaConversions

trait AccountHelpers {

  implicit def app: play.api.Application

  /**
   * Default group(s) new users belong to.
   */
  def defaultPortalGroups: List[String] =
    app.configuration.getStringList("ehri.portal.defaultUserGroups")
      .map(JavaConversions.collectionAsScalaIterable(_).toList)
      .getOrElse(List.empty)

  /**
   * Whether new users are signed up for messaging or not.
   */
  def canMessage: Boolean =
    app.configuration.getBoolean("ehri.users.messaging.default").getOrElse(false)

  def minPasswordLength: Int =
    app.configuration.getInt("ehri.passwords.minLength").getOrElse(6)
}
