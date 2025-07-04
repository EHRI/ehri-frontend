package guice

import auth.handler.AuthIdContainer
import auth.handler.cookie.CookieIdContainer
import auth.oauth2.OAuth2Config
import auth.oauth2.providers._
import com.google.inject.AbstractModule
import play.api.Configuration
import services.accounts.{AccountManager, SqlAccountManager}
import services.oauth2.{OAuth2Service, WebOAuth2Service}
import views.AppConfig

import javax.inject.{Inject, Provider}

private class OAuth2ConfigProvider @Inject()(config: Configuration, appConfig: AppConfig) extends Provider[OAuth2Config] {
  override def get(): OAuth2Config = (login: Boolean) => {
    val all = Seq(
      GoogleOAuth2Provider(config),
      MicrosoftOAuth2Provider(config),
      FacebookOAuth2Provider(config),
      YahooOAuth2Provider(config),
      ORCIDOAuth2Provider(config)
    )
    val list = if (login) appConfig.oauth2LoginProviders
    else appConfig.oauth2RegistrationProviders

    all.filter(p => list.contains(p.name))
  }
}

class AuthModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[AuthIdContainer]).to(classOf[CookieIdContainer])
    bind(classOf[AccountManager]).to(classOf[SqlAccountManager])
    bind(classOf[OAuth2Service]).to(classOf[WebOAuth2Service])
    bind(classOf[OAuth2Config]).toProvider(classOf[OAuth2ConfigProvider])
  }
}
