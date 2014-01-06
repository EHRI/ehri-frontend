package controllers.core.oauth2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.Play._
import play.api.libs.ws.Response

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object GoogleOAuth2Provider extends OAuth2Provider {

  def settings: Settings = Settings(
    current.configuration.getString("securesocial.google." + OAuth2Settings.ClientId).get,
    current.configuration.getString("securesocial.google." + OAuth2Settings.ClientSecret).get,
    current.configuration.getBoolean("securesocial.ssl").get
  )

  def getUserData(response: Response): UserData = response.json.as[UserData]((
    (__ \ Email).read[String] and
    (__ \ Name).read[String] and
    (__ \ Picture).read[String]
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

  val authorizationUrl = "https://accounts.google.com/o/oauth2/auth"
  val userInfoUrl = "https://www.googleapis.com/oauth2/v1/userinfo"
  val accessTokenUrl = "https://accounts.google.com/o/oauth2/token"
  val oauth2Scopes = "https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"

}
