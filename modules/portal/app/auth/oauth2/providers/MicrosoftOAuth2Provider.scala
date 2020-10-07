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
        guid <- (json \ "id").asOpt[String]
        // NB: Microsoft doesn't make it easy to get emails: the "mail" property
        // ostensibly contains the email but might well be empty because the user
        // or organisation has to set this explicitly. OTOH the "userPrincipalName"
        // field is always populated with an email-format address but it *might* not
        // be exactly what the user knows as their email address - at least it should
        // be an alias however. In my case, with a KCL-email address run by MS the
        // "userPrincipalName" field gives my correct email address whereas "mail"
        // is null.
        // Also note: for registration and login via OAuth2 the user's email is
        // pre-verified so it's not vital they can receive mail through it.
        email <- (json \ "mail").asOpt[String].filter(_.nonEmpty)
          .orElse((json \ "userPrincipalName").asOpt[String])
        name <- (json \ "displayName").asOpt[String]
      } yield UserData(
        providerId = guid,
        email = email,
        name = name,
        imageUrl = None
      )
    } catch {
      case e: JsonParseException =>
      logger.error(s"Unexpected OAuth profile data: $data", e)
        None
    }
  }
}
