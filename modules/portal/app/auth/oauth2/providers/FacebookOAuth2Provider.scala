package auth.oauth2.providers

import auth.oauth2.{UserData, OAuth2Constants, OAuth2Info}
import play.api.Logger
import play.api.libs.json._
import play.api.libs.ws.WSResponse

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object FacebookOAuth2Provider extends OAuth2Provider {
  val name = "facebook"

  // facebook does not follow the OAuth2 spec :-\
  override def buildOAuth2Info(response: WSResponse): Option[OAuth2Info] = {
    response.body.split("&|=") match {
      case Array(OAuth2Constants.AccessToken, token, "expires", expiresIn) => Some(OAuth2Info(token, None, Some(expiresIn.toInt)))
      case Array(OAuth2Constants.AccessToken, token) => Some(OAuth2Info(token))
      case e => None
    }
  }

  def getUserData(response: WSResponse): Option[UserData] = {
    val json: JsValue = response.json
    Logger.debug("Facebook user info: " + Json.prettyPrint(json))

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
  }
}
