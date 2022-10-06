package auth.oauth2.providers

import auth.oauth2.{OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import play.api.http.HeaderNames
import play.api.libs.json._

case class MicrosoftOAuth2Provider(config: play.api.Configuration) extends OAuth2Provider {
  val name = "microsoft"

  override def getUserInfoParams(info: OAuth2Info): Seq[(String, String)] = Seq.empty

  override def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] =
    Seq(HeaderNames.AUTHORIZATION -> s"Bearer ${info.accessToken}")

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      logger.debug(s"Microsoft user info $json")
      for {
        // https://learn.microsoft.com/en-us/azure/active-directory/develop/userinfo#calling-the-api
        // URL is: https://graph.microsoft.com/oidc/userinfo/
        guid <- (json \ "sub").asOpt[String]
        email <- (json \ "email").asOpt[String]
        name <- (json \ "name").asOpt[String]
        picture = (json \ "picture").asOpt[String]
      } yield UserData(
        providerId = guid,
        email = email,
        name = name,
        imageUrl = picture
      )
    } catch {
      case e: JsonParseException =>
      logger.error(s"Unexpected OAuth profile data: $data", e)
        None
    }
  }
}
