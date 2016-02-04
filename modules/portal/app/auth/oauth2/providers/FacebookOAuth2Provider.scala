package auth.oauth2.providers

import javax.inject.Inject

import auth.oauth2.{OAuth2Constants, OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._

case class FacebookOAuth2Provider (config: play.api.Configuration) extends OAuth2Provider {
  val name = "facebook"

  override def getUserInfoParams(info: OAuth2Info): Seq[(String,String)] =
    super.getUserInfoParams(info) ++ Seq(
      "fields" -> "name,first_name,last_name,picture,email",
      "return_ssl_resources" -> "1"
    )

  // facebook does not follow the OAuth2 spec :-\
  override def parseAccessInfo(data: String): Option[OAuth2Info] = {
    data.split("&|=") match {
      case Array(OAuth2Constants.AccessToken, token, "expires", expiresIn) => Some(OAuth2Info(token, None, Some(expiresIn.toInt)))
      case Array(OAuth2Constants.AccessToken, token) => Some(OAuth2Info(token))
      case e => None
    }
  }

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      for {
        guid <- (json \ "id").asOpt[String]
        email <- (json \ "email").asOpt[String]
        name <- (json \ "name").asOpt[String]
        imageUrl <- (json \ "picture" \ "data" \ "url").asOpt[String]
      } yield UserData(
        providerId = guid,
        email = email,
        name = name,
        imageUrl = imageUrl
      )
    } catch {
      case e: JsonParseException => None
    }
  }
}
