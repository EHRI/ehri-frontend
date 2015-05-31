package auth.oauth2.providers

import javax.inject.Inject

import auth.oauth2.UserData

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class LinkedInOAuth2Provider @Inject() (implicit val app: play.api.Application) extends OAuth2Provider {
  val name = "linkedin"

  def parseUserInfo(data: String): Option[UserData] = ???
}
