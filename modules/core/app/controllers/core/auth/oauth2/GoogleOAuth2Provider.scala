package controllers.core.auth.oauth2

import play.api.libs.json._
import play.api.libs.functional.syntax._
import play.api.libs.ws.Response
import play.api.Logger

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object GoogleOAuth2Provider extends OAuth2Provider {

  val name = "google"

  def getUserData(response: Response): UserData = {
    Logger.debug("User info: " + Json.prettyPrint(response.json))
    response.json.as[UserData]((
      (__ \ Id).read[String] and
      (__ \ Email).read[String] and
      (__ \ Name).read[String] and
      (__ \ Picture).read[String]
    )(UserData.apply _))
  }

  val Error = "error"
  val Message = "message"
  val Type = "type"
  val Id = "id"
  val Name = "name"
  val GivenName = "given_name"
  val FamilyName = "family_name"
  val Picture = "picture"
  val Email = "email"
}
