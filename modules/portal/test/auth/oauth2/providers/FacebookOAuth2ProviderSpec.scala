package auth.oauth2.providers

import auth.oauth2.{OAuth2Constants, OAuth2Info}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.PlaySpecification
import helpers.ResourceUtils

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
class FacebookOAuth2ProviderSpec extends PlaySpecification with ResourceUtils {

  def testAccessData = resourceAsString("facebookAccessData.txt")
  def testUserData = resourceAsString("facebookUserData.txt")
  implicit val app = new GuiceApplicationBuilder().build()

   "Facebook OAuth2 provider" should {
     "parse access data" in {
       FacebookOAuth2Provider().parseAccessInfo(testAccessData) must beSome.which { d =>
         d.accessToken must equalTo("some-access-token")
         d.refreshToken must equalTo(None)
         d.expiresIn must equalTo(Some(100))
       }
     }

     "parse user data" in {
       FacebookOAuth2Provider().parseUserInfo(testUserData) must beSome.which { d =>
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
       FacebookOAuth2Provider().getUserInfoParams(OAuth2Info("MY-TOKEN")) must equalTo(expected)
     }
   }
 }
