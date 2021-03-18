package auth.oauth2.providers

import auth.oauth2.{OAuth2Constants, OAuth2Info}
import helpers.ResourceUtils
import play.api.test.PlaySpecification
import play.api.{Configuration, Environment}

/**
   */
class FacebookOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  def testAccessData = resourceAsString("facebookAccessData.json")
  def testUserData = resourceAsString("facebookUserData.json")
  val config = Configuration.load(Environment.simple())

   "Facebook OAuth2 provider" should {
     "parse access data" in {
       FacebookOAuth2Provider(config).parseAccessInfo(testAccessData) must beSome.which { d =>
         d.accessToken must equalTo("some-access-token")
         d.refreshToken must beNone
         d.expiresIn must beSome(100)
       }
     }

     "parse user data" in {
       FacebookOAuth2Provider(config).parseUserInfo(testUserData) must beSome.which { d =>
         d.name must equalTo("Any Name")
         d.email must equalTo("example1@example.com")
         d.providerId must equalTo("123456789")
       }
     }

     "generate the right user access params" in {
       val expected = Seq(
         OAuth2Constants.AccessToken -> "MY-TOKEN",
        "fields" -> "name,first_name,last_name,picture,email",
        "return_ssl_resources" -> "1"
       )
       FacebookOAuth2Provider(config).getUserInfoParams(OAuth2Info("MY-TOKEN")) must equalTo(expected)
     }
   }
 }
