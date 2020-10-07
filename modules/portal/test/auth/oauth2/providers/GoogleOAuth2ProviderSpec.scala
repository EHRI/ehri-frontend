package auth.oauth2.providers

import auth.oauth2.{OAuth2Info, OAuth2Constants}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification
import helpers.ResourceUtils

class GoogleOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  def testAccessData = resourceAsString("googleAccessData.json")
  def testUserData = resourceAsString("googleUserData.json")
  val config = new GuiceApplicationBuilder().build().configuration

  "Google OAuth2 provider" should {
    "parse access data" in {
      GoogleOAuth2Provider(config).parseAccessInfo(testAccessData) must beSome.which { d =>
        d.accessToken must equalTo("some-access-token")
        d.refreshToken must beNone
        d.expiresIn must beSome(100)
        d.tokenType must beSome("Bearer")
      }
    }

    "parse user data" in {
      GoogleOAuth2Provider(config).parseUserInfo(testUserData) must beSome.which { d =>
        d.name must equalTo("Any Name")
        d.email must equalTo("example1@example.com")
        d.providerId must equalTo("123456789")
      }
    }

    "generate the right user access params" in {
      val expected = Seq(OAuth2Constants.AccessToken -> "MY-TOKEN")
      GoogleOAuth2Provider(config).getUserInfoParams(OAuth2Info("MY-TOKEN")) must equalTo(expected)
    }
  }
}
