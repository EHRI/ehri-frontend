package auth.oauth2

import javax.inject.Inject

import auth.AuthenticationError
import auth.oauth2.providers.OAuth2Provider
import play.api.Logger
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

case class WebOAuth2Flow @Inject ()(implicit config: play.api.Configuration, ws: WSClient) extends OAuth2Flow {

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] = {
    val accessTokenUrl: String = provider.getAccessTokenUrl
    Logger.debug(s"Fetching access token for provider ${provider.name} at $accessTokenUrl")
    ws.url(accessTokenUrl)
      .withHeaders(provider.getAccessTokenHeaders: _*)
      .post(provider.getAccessTokenParams(code, handlerUrl))
      .map { r =>
      Logger.trace(s"Access Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.parseAccessInfo(r.body).getOrElse {
        throw new AuthenticationError(s"Unable to fetch access token and info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] = {
    val url: String = provider.getUserInfoUrl(info)
    val headers: Seq[(String, String)] = provider.getUserInfoHeader(info)
    Logger.debug(s"Fetching info at $url with headers $headers")
    ws.url(url)
      .withQueryString(provider.getUserInfoParams(info): _*)
      .withHeaders(headers: _*).get()
      .map { r =>
      Logger.trace(s"User Info Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.parseUserInfo(r.body).getOrElse{
        throw new AuthenticationError(s"Unable to fetch user info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }
}
