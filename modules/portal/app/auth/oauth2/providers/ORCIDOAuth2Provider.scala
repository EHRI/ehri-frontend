package auth.oauth2.providers

import auth.AuthenticationError
import auth.oauth2.{OAuth2Constants, OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import play.api.http.HeaderNames
import play.api.libs.json._

case class ORCIDOAuth2Provider(config: play.api.Configuration) extends OAuth2Provider {
  val name = "orcid"
  override def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] =
    Seq(HeaderNames.AUTHORIZATION -> s"Bearer ${info.accessToken}")

  /**
    * Parse the provider's access token info.
    *
    * @param data the provider's access token response body.
    * @return access token data
    */
  override def parseAccessInfo(data: String): Option[OAuth2Info] = {
    try {
      val json = Json.parse(data)
      logger.trace("Parsing ORCID data: " + json)
      for {
        accessToken <- (json \ OAuth2Constants.AccessToken).asOpt[String]
      } yield OAuth2Info(
        accessToken,
        tokenType = (json \ OAuth2Constants.TokenType).asOpt[String],
        expiresIn = (json \ OAuth2Constants.ExpiresIn).asOpt[Int],
        refreshToken = (json \ OAuth2Constants.RefreshToken).asOpt[String],
        userGuid = (json \ "orcid").asOpt[String]
      )
    } catch {
      case e: JsonParseException =>
        logger.error("Unable to parse ORCID access token info: " + e.getMessage)
        None
    }
  }

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json: JsValue = Json.parse(data)
      logger.debug(s"ORCID user info $json")
      (for {
        guid <- (json \ "sub").asOpt[String]
        email <- (json \ "email").asOpt[String]
        name <- (json \ "name").asOpt[String]
      } yield UserData(
        providerId = guid,
        email = email,
        name = name,
        imageUrl = None
      )).orElse {
        // ORCID's OAuth2 userinfo endpoint does not return the user's email unless it
        // is set to public or limited. If the email is not found, we throw an error,
        // because we need one to create a user account.
        if ((json \ "email").asOpt[String].isEmpty) {
          throw AuthenticationError("Unable to fetch user info for ORCID, email not found",
            Some("login.error.oauth2.orcid.missingEmail"))
        }
        None
      }
    } catch {
      case e: JsonParseException =>
        None
    }
  }
}
