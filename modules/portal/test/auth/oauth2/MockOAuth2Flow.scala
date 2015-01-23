package auth.oauth2

import auth.oauth2.providers.OAuth2Provider

import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.io.Source

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
case class MockOAuth2Flow() extends OAuth2Flow {

  private def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))
    .getLines().mkString("\n")

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] =
    immediate(provider.buildOAuth2Info(resourceAsString(provider.name + "AccessData.txt")).get)

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] =
    immediate(provider.getUserData(resourceAsString(provider.name + "UserData.txt")).get)
}
