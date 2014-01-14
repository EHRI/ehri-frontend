package controllers.core.auth.oauth2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws.Response
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object FacebookOauth2Provider extends OAuth2Provider {
  val name = "facebook"

  // facebook does not follow the OAuth2 spec :-\
  override def buildOAuth2Info(response: Response): OAuth2Info = {
    response.body.split("&|=") match {
      case Array(OAuth2Constants.AccessToken, token, "expires", expiresIn) => OAuth2Info(token, None, Some(expiresIn.toInt))
      case Array(OAuth2Constants.AccessToken, token) => OAuth2Info(token)
      case e =>
        Logger.error("[securesocial] invalid response format for accessToken")
        sys.error("Authentication error: " )
    }
  }

  def getUserData(response: Response): UserData = {
    Logger.debug("Facebook user info: " + Json.prettyPrint(response.json))
    response.json.as[UserData]((
      (__ \ Id).read[String] and
      (__ \ Email).read[String] and
      (__ \ Name).read[String] and
      (__ \ Picture \ "data" \ "url").read[String]
    )(UserData.apply _))
  }

  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Id = "id"
  val Name = "name"
  val GivenName = "given_name"
  val FamilyName = "family_name"
  val Picture = "picture"
  val Email = "email"
}
