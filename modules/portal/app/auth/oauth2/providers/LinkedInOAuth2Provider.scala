package auth.oauth2.providers

import auth.oauth2.UserData
import play.api.libs.ws.WSResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object LinkedInOAuth2Provider extends OAuth2Provider {
  val name = "linkedin"

  def getUserData(response: WSResponse): Option[UserData] = ???

}
