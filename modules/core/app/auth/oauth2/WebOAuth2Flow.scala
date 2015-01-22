package auth.oauth2

import auth.AuthenticationError
import auth.oauth2.providers.OAuth2Provider
import play.api.Logger
import play.api.libs.ws.WS

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class WebOAuth2Flow()(implicit app: play.api.Application) extends OAuth2Flow {

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] = {
    Logger.debug(s"Fetching access token at ${provider.settings.accessTokenUrl}")
    WS.url(provider.getAccessTokenUrl)
      .withHeaders(provider.getAccessTokenHeaders: _*)
      .post(provider.getAccessTokenParams(code, handlerUrl))
      .map { r =>
      Logger.trace(s"Access Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.buildOAuth2Info(r.body).getOrElse {
        throw new AuthenticationError(s"Unable to fetch access token and info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] = {
    val url: String = provider.getUserInfoUrl(info)
    val headers: Seq[(String, String)] = provider.getUserInfoHeader(info)
    Logger.debug(s"Fetching info at $url with headers $headers")
    WS.url(url)
      .withHeaders(headers: _*).get()
      .map { r =>
      Logger.trace(s"User Info Data for OAuth2 ${provider.name}:-------\n${r.body}\n-----")
      provider.getUserData(r.body).getOrElse{
        throw new AuthenticationError(s"Unable to fetch user info for provider ${provider.name} " +
          s" via response data: ${r.body}")
      }
    }
  }
}
