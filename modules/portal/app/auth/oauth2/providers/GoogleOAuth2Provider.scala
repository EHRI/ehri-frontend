package auth.oauth2.providers

import auth.oauth2.UserData
import com.fasterxml.jackson.core.JsonParseException
import play.api.Logger
import play.api.libs.json._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object GoogleOAuth2Provider extends OAuth2Provider {

  val name = "google"

  override def getUserData(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      Logger.debug("User info: " + Json.prettyPrint(json))

      for {
        guid <- (json \ "id").asOpt[String]
        email <- (json \ "email").asOpt[String]
        name <- (json \ "name").asOpt[String]
        imageUrl <- (json \ "picture").asOpt[String]
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
