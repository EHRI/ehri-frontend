package auth.oauth2.providers

import javax.inject.Inject

import auth.oauth2.UserData
import com.fasterxml.jackson.core.JsonParseException
import play.api.libs.json._

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class GoogleOAuth2Provider @Inject ()(implicit val app: play.api.Application) extends OAuth2Provider {

  val name = "google"

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
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
