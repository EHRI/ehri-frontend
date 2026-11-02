package auth.oauth2

import auth.oauth2.providers.OAuth2Provider
import play.api.Configuration
import services.oauth2.OAuth2Service

import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.Future.{successful => immediate}
import scala.io.Source

/** User data can be overridden via `test.oauth2.{provider}.userData`. */
case class MockOAuth2Service @Inject()(config: Configuration) extends OAuth2Service {

  private def resourceAsString(name: String): String = Source.fromInputStream(getClass
    .getClassLoader.getResourceAsStream(name))
    .getLines().mkString("\n")

  override def getAccessToken(provider: OAuth2Provider, handlerUrl: String, code: String): Future[OAuth2Info] =
    immediate(provider.parseAccessInfo(resourceAsString(provider.name + "AccessData.json")).get)

  override def getUserData(provider: OAuth2Provider, info: OAuth2Info): Future[UserData] = {
    val data = config.getOptional[String](s"test.oauth2.${provider.name}.userData")
      .getOrElse(resourceAsString(provider.name + "UserData.json"))
    immediate(provider.parseUserInfo(data).get)
  }
}
