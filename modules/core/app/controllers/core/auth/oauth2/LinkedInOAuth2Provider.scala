package controllers.core.auth.oauth2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws.WSResponse
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object LinkedInOAuth2Provider extends OAuth2Provider {
  val name = "linkedin"

  def getUserData(response: WSResponse): Option[UserData] = ???

}
