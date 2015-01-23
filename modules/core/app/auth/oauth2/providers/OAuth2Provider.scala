package auth.oauth2.providers

import java.net.URLEncoder

import auth.oauth2._
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json.Json

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait OAuth2Provider {


  val name: String

  def buildRedirectUrl[A](handlerUrl: String, state: String): String = {
    val params = Seq(
      OAuth2Constants.ClientId -> settings.clientId,
      OAuth2Constants.RedirectUri -> handlerUrl,
      OAuth2Constants.ResponseType -> OAuth2Constants.Code,
      OAuth2Constants.State -> state,
      OAuth2Constants.Scope -> settings.scope
    )
    settings.authorizationUrl +
      params.map( p => p._1 + "=" + URLEncoder.encode(p._2, "UTF-8")).mkString("?", "&", "")
  }

  def getUserData(data: String): Option[UserData]

  def getUserInfoUrl(info: OAuth2Info): String = settings.userInfoUrl + info.accessToken

  def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] = Seq.empty

  def getAccessTokenUrl: String = settings.accessTokenUrl

  def getAccessTokenHeaders: Seq[(String, String)] = Seq.empty

  def getAccessTokenParams(code: String, handlerUrl: String): Map[String,Seq[String]] = Map(
    OAuth2Constants.ClientId -> Seq(settings.clientId),
    OAuth2Constants.ClientSecret -> Seq(settings.clientSecret),
    OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
    OAuth2Constants.Code -> Seq(code),
    OAuth2Constants.RedirectUri -> Seq(handlerUrl)
  )

  def buildOAuth2Info(data: String): Option[OAuth2Info] = {
    try {
      val json = Json.parse(data)
      for {
        accessToken <- (json \ OAuth2Constants.AccessToken).asOpt[String]
      } yield OAuth2Info(
        accessToken,
        (json \ OAuth2Constants.TokenType).asOpt[String],
        (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
        (json \ OAuth2Constants.RefreshToken).asOpt[String]
      )
    } catch {
      case e: JsonParseException => None
    }
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

