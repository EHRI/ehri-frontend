package auth.oauth2.providers

import auth.oauth2.UserData
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._

case class GoogleOAuth2Provider(config: play.api.Configuration) extends OAuth2Provider {
  val name = "google"

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      logger.debug(s"Google user info $json")
      for {
        guid <- (json \ "id").asOpt[String]
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
      case e: JsonParseException => None
    }
  }
}
