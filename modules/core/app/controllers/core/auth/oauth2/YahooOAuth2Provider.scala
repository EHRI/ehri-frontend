package controllers.core.auth.oauth2

import play.api.Logger
import play.api.http.ContentTypes
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import org.apache.commons.codec.binary.Base64

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object YahooOAuth2Provider extends OAuth2Provider {

  val name = "yahoo"

  private case class YahooEmail(handle: String, id: Int, primary: Option[Boolean])
  private object YahooEmail {
    implicit val reader = Json.reads[YahooEmail]
  }

  override def getUserInfoUrl(info: OAuth2Info): String =
    settings.userInfoUrl + info.userGuid.getOrElse("NO-ID") + "/profile?format=json"

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
      "redirect_uri" -> Seq(handlerUrl),
      "code" -> Seq(code)
    )

  def getUserData(response: WSResponse): Option[UserData] = {
    Logger.debug("Yahoo user info: " + response.body)

    val json = response.json
    val guid = (json \ "profile" \ "guid").asOpt[String]
    val givenName = (json \ "profile" \ "givenName").asOpt[String]
    val familyName = (json \ "profile" \ "familyName").asOpt[String]
    val imageUrl = (json \ "profile" \ "image" \ "imageUrl").asOpt[String]
    val emails: Option[Seq[YahooEmail]] = (json \ "profile" \ "emails").asOpt[Seq[YahooEmail]]

    for {
      guid <- (json \ "profile" \ "guid").asOpt[String]
      givenName <- (json \ "profile" \ "givenName").asOpt[String]
      familyName <- (json \ "profile" \ "familyName").asOpt[String]
      imageUrl <- (json \ "profile" \ "image" \ "imageUrl").asOpt[String]
      emails <- (json \ "profile" \ "emails").asOpt[Seq[YahooEmail]]
      mainEmail <- emails.sortBy(_.primary.isEmpty).headOption.map(_.handle)
    } yield UserData(
      providerId = guid,
      email = mainEmail,
      name = s"$givenName $familyName",
      imageUrl = imageUrl
    )
  }

  override   def buildOAuth2Info(response: WSResponse): OAuth2Info = {
    val info: OAuth2Info = super.buildOAuth2Info(response)
    val yinfo: OAuth2Info = info.copy(userGuid = (response.json \ "xoauth_yahoo_guid").asOpt[String])
    Logger.debug("Yahoo info: " + yinfo)
    yinfo
  }
}
