package auth.oauth2.providers

import play.api.test.PlaySpecification
import helpers.ResourceUtils

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class GoogleOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  def testAccessData = resourceAsString("googleAccessData.txt")
  def testUserData = resourceAsString("googleUserData.txt")

  "Google OAuth2 provider" should {
    "parse access data" in {
      GoogleOAuth2Provider.buildOAuth2Info(testAccessData) must beSome.which { d =>
        d.accessToken must equalTo("some-access-token")
        d.refreshToken must equalTo(None)
        d.expiresIn must equalTo(Some(100))
        d.tokenType must equalTo(Some("Bearer"))
      }
    }

    "parse user data" in {
      GoogleOAuth2Provider.getUserData(testUserData) must beSome.which { d =>
        d.name must equalTo("Any Name")
        d.email must equalTo("example1@example.com")
        d.providerId must equalTo("123456789")
      }
    }
  }
}
