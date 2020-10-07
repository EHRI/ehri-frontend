package auth.oauth2

import auth.oauth2.providers.OAuth2Provider
import services.oauth2.OAuth2Service

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.io.Source

case class MockOAuth2Service() extends OAuth2Service {

  private def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))
    .getLines().mkString("\n")

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] =
    immediate(provider.parseAccessInfo(resourceAsString(provider.name + "AccessData.json")).get)

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] =
    immediate(provider.parseUserInfo(resourceAsString(provider.name + "UserData.json")).get)
}
