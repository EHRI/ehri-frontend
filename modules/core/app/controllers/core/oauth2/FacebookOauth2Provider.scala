package controllers.core.oauth2

import play.api.libs.ws.Response
import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.mvc.Controller
import play.api.Play._
import play.api.libs.ws.Response
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object FacebookOauth2Provider extends OAuth2Provider {

  // facebook does not follow the OAuth2 spec :-\
  override def buildOAuth2Info(response: Response): OAuth2Info = {
    response.body.split("&|=") match {
      case Array("access_token", token, "expires", expiresIn) => OAuth2Info(token, None, Some(expiresIn.toInt))
      case Array("access_token", token) => OAuth2Info(token)
      case e =>
        Logger.error("[securesocial] invalid response format for accessToken")
        sys.error("Authentication error: " )
    }
  }

  def settings: Settings = Settings(
    current.configuration.getString("securesocial.facebook." + OAuth2Settings.ClientId).get,
    current.configuration.getString("securesocial.facebook." + OAuth2Settings.ClientSecret).get,
    current.configuration.getBoolean("securesocial.ssl").get
  )

  def getUserData(response: Response): UserData = response.json.as[UserData]((
    (__ \ Email).read[String] and
    (__ \ Name).read[String] and
    (__ \ Picture \ "data" \ "url").read[String]
  )(UserData.apply _))

  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Id = "id"
  val Name = "name"
  val GivenName = "given_name"
  val FamilyName = "family_name"
  val Picture = "picture"
  val Email = "email"

  val authorizationUrl = "https://graph.facebook.com/oauth/authorize"
  val userInfoUrl = "https://graph.facebook.com/me?fields=name,first_name,last_name,picture,email&return_ssl_resources=1"
  val accessTokenUrl = "https://graph.facebook.com/oauth/access_token"
  val oauth2Scopes = "email"

}
