package auth.oauth2.providers

import helpers.ResourceUtils
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

/**
   */
class YahooOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  val testAccessData = resourceAsString("yahooAccessData.json")
  val testUserData = resourceAsString("yahooUserData.json")
  val config = Configuration.load(Environment.simple())

  "Yahoo OAuth2 provider" should {
    "parse access data" in {
      YahooOAuth2Provider(config).parseAccessInfo(testAccessData) must beSome.which { d =>
        d.accessToken must equalTo("some-access-token")
        d.userGuid must beSome("123456789")
        d.refreshToken must beSome("blah")
        d.expiresIn must beSome(100)
        d.tokenType must beSome("bearer")
      }
    }

     "parse user data" in {
       YahooOAuth2Provider(config).parseUserInfo(testUserData) must beSome.which { d =>
         d.name must equalTo("Jasmine Smith")
         d.email must equalTo("yqa_functest_15572415322065371@yahoo.com")
         d.providerId must equalTo("JEF4XR2CT55JPVEBVD7ZVT6A3A")
       }
     }
   }
 }
