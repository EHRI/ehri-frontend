package auth.oauth2.providers

import auth.oauth2.{OAuth2Constants, OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import org.apache.commons.codec.binary.Base64
import play.api.http.{ContentTypes, HeaderNames}
import play.api.libs.json.{JsValue, Json}


case class YahooOAuth2Provider (config: play.api.Configuration) extends OAuth2Provider {

  val name = "yahoo"

  override def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] =
    Seq(HeaderNames.AUTHORIZATION -> s"Bearer ${info.accessToken}")

  override def getAccessTokenHeaders: Seq[(String, String)] = {
    // Base64 encode the clientId:clientSecret
    val encoded: String = new Base64()
      .encodeToString(s"${settings.clientId}:${settings.clientSecret}".getBytes)
    Seq(
      HeaderNames.AUTHORIZATION -> s"Basic $encoded",
      HeaderNames.CONTENT_TYPE -> ContentTypes.FORM
    )
  }

  override def getAccessTokenParams(code: String, handlerUrl: String): Map[String,Seq[String]] =
    Map(
      OAuth2Constants.GrantType -> Seq(OAuth2Constants.AuthorizationCode),
      OAuth2Constants.RedirectUri -> Seq(handlerUrl),
      OAuth2Constants.Code -> Seq(code)
    )

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      logger.debug(s"Yahoo user info $json")
      for {
        guid <- (json \ "sub").asOpt[String]
        email <- (json \ "email").asOpt[String]
        name <- (json \ "name").asOpt[String]
        imageUrl = (json \ "picture").asOpt[String]
      } yield UserData(
        providerId = guid,
        email = email,
        name = name,
        imageUrl = imageUrl
      )
    } catch {
      case e: JsonParseException =>
        logger.error(s"Error parsing Yahoo user data: ${e.getMessage}")
        None
    }
  }

  override def parseAccessInfo(data: String): Option[OAuth2Info] = {
    val info: Option[OAuth2Info] = super.parseAccessInfo(data)
    try {
      info.map(_.copy(userGuid = (Json.parse(data) \ "xoauth_yahoo_guid").asOpt[String]))
    } catch {
      case _: JsonParseException => None
    }
  }
}
