package auth.oauth2.providers

import auth.oauth2.{OAuth2Constants, OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object FacebookOAuth2Provider extends OAuth2Provider {
  val name = "facebook"

  // facebook does not follow the OAuth2 spec :-\
  override def buildOAuth2Info(data: String): Option[OAuth2Info] = {
    data.split("&|=") match {
      case Array(OAuth2Constants.AccessToken, token, "expires", expiresIn) => Some(OAuth2Info(token, None, Some(expiresIn.toInt)))
      case Array(OAuth2Constants.AccessToken, token) => Some(OAuth2Info(token))
      case e => None
    }
  }

  override def getUserData(data: String): Option[UserData] = {
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
