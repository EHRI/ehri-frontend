package controllers.core.auth.oauth2

import play.api.http.ContentTypes
import play.api.libs.ws.WSResponse
import org.apache.commons.codec.binary.Base64

/**
 * TODO: Complete the implementation when we can figure out
 * how to get the user's name, email, and image from the
 * Yahoo API.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
object YahooOAuth2Provider extends OAuth2Provider {

  val name = "yahoo"

  override def getUserInfoUrl(info: OAuth2Info): String = settings.userInfoUrl

  override def getUserInfoHeader(info: OAuth2Info): Seq[(String, String)] =
    Seq("Authorization" -> s"Bearer ${info.accessToken}")

  override def getAccessTokenHeaders: Seq[(String, String)] = {
    // Base64 encode the clientId:clientSecret
    val encoded: String = new Base64()
      .encodeToString(s"${settings.clientId}:${settings.clientSecret}".getBytes)
    Seq(
      "Authorization" -> s"Basic $encoded",
      "Content-Type" -> ContentTypes.FORM
    )
  }

  override def getAccessTokenParams(code: String, handlerUrl: String): Map[String,Seq[String]] =
    Map(
      "grant_type" -> Seq("authorization_code"),
      "redirect_uri" -> Seq(redirectUri.getOrElse(handlerUrl)),
      "code" -> Seq(code)
    )

  def getUserData(response: WSResponse): UserData = ???

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
