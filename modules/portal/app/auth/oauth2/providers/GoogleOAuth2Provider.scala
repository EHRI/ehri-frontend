package auth.oauth2.providers

import auth.oauth2.UserData
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object GoogleOAuth2Provider extends OAuth2Provider {

  val name = "google"

  def getUserData(response: WSResponse): Option[UserData] = {
    val json: JsValue = response.json
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
  }
}
