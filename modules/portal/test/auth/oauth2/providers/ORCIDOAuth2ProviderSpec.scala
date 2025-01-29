package auth.oauth2.providers

import auth.oauth2.OAuth2Info
import helpers.ResourceUtils
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

/**
  */
class ORCIDOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  val testAccessData = resourceAsString("orcidAccessData.json")
  val config = Configuration.load(Environment.simple())

  "ORCID OAuth2 provider" should {
    "parse access data" in {
      ORCIDOAuth2Provider(config).parseAccessInfo(testAccessData) must beSome.which { (d: OAuth2Info) =>
        d.accessToken must equalTo("some-access-token")
        d.userGuid must beSome("1234-5678-1234-5678")
        d.refreshToken must beSome("blah")
        d.expiresIn must beSome(100)
        d.tokenType must beSome("bearer")
      }
    }
  }
}
