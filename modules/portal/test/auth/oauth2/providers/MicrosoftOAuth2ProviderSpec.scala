package auth.oauth2.providers

import auth.oauth2.OAuth2Info
import helpers.ResourceUtils
import play.api.http.HeaderNames
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

class MicrosoftOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  def testAccessData = resourceAsString("microsoftAccessData.json")
  def testUserData = resourceAsString("microsoftUserData.json")
  val config = Configuration.load(Environment.simple())

  "Microsoft OAuth2 provider" should {
    "parse access data" in {
      MicrosoftOAuth2Provider(config).parseAccessInfo(testAccessData) must beSome.which { d =>
        d.accessToken must equalTo("some-access-token")
        d.refreshToken must beSome("some-refresh-token")
        d.expiresIn must beSome(100)
        d.tokenType must beSome("Bearer")
      }
    }

    "parse user data" in {
      MicrosoftOAuth2Provider(config).parseUserInfo(testUserData) must beSome.which { d =>
        d.name must equalTo("Joe Blogs")
        d.email must equalTo("example2@example.com")
        d.providerId must equalTo("123456789")
      }
    }

    "generate the right user access headers" in {
      val expected = Seq(HeaderNames.AUTHORIZATION -> "Bearer MY-TOKEN")
      MicrosoftOAuth2Provider(config).getUserInfoHeader(OAuth2Info("MY-TOKEN")) must equalTo(expected)
    }
  }
}
