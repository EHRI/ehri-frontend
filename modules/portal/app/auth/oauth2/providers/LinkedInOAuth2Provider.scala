package auth.oauth2.providers

import auth.oauth2.UserData

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class LinkedInOAuth2Provider (config: play.api.Configuration) extends OAuth2Provider {
  val name = "linkedin"

  def parseUserInfo(data: String): Option[UserData] = ???
}
