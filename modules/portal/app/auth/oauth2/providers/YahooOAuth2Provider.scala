package auth.oauth2.providers

import javax.inject.Inject

import auth.oauth2.{OAuth2Info, UserData}
import com.fasterxml.jackson.core.JsonParseException
import org.apache.commons.codec.binary.Base64
import play.api.http.ContentTypes
import play.api.libs.json.Json

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class YahooOAuth2Provider @Inject()(implicit val app: play.api.Application) extends OAuth2Provider {

  val name = "yahoo"

  private case class YahooEmail(handle: String, id: Int, primary: Option[Boolean])
  private object YahooEmail {
    implicit val reader = Json.reads[YahooEmail]
  }

  override def getUserInfoUrl(info: OAuth2Info): String =
    settings.userInfoUrl + info.userGuid.getOrElse("NO-ID") + "/profile"

  override def getUserInfoParams(info: OAuth2Info): Seq[(String,String)] =
    Seq("format" -> "json")

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

  override def parseUserInfo(data: String): Option[UserData] = {
    try {
      val json = Json.parse(data)
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
    } catch {
      case e: JsonParseException => None
    }
  }

  override def parseAccessInfo(data: String): Option[OAuth2Info] = {
    val info: Option[OAuth2Info] = super.parseAccessInfo(data)
    try {
      info.map(_.copy(userGuid = (Json.parse(data) \ "xoauth_yahoo_guid").asOpt[String]))
    } catch {
      case e: JsonParseException => None
    }
  }
}
