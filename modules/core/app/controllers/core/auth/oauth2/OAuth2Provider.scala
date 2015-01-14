package controllers.core.auth.oauth2

import play.api.libs.ws.WSResponse
import play.api.Logger
import play.api.libs.json.Json

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2Provider {


  val name: String

  def getUserData(response: WSResponse): UserData

  def getUserInfoUrl(info: OAuth2Info): String = settings.userInfoUrl + info.accessToken

  def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] = Seq.empty

  def buildOAuth2Info(response: WSResponse): OAuth2Info = {
    val json = response.json
    if ( Logger.isDebugEnabled ) {
      Logger.debug("[securesocial] got json back [" + Json.prettyPrint(json) + "]")
    }
    OAuth2Info(
      (json \ OAuth2Constants.AccessToken).as[String],
      (json \ OAuth2Constants.TokenType).asOpt[String],
      (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
      (json \ OAuth2Constants.RefreshToken).asOpt[String]
    )
  }

  protected def getSetting(key: String): String = {
    import play.api.Play.current
    val keyName = "securesocial." + name + "." + key
    current.configuration.getString(keyName)
      .getOrElse(sys.error("Configuration key not found: " + keyName))
  }

  def settings: ProviderSettings = ProviderSettings(
    getSetting(OAuth2Settings.ClientId),
    getSetting(OAuth2Settings.ClientSecret),
    getSetting(OAuth2Settings.AuthorizationUrl),
    getSetting(OAuth2Settings.AccessTokenUrl),
    getSetting(OAuth2Settings.UserInfoUrl),
    getSetting(OAuth2Settings.Scope)
  )
}

