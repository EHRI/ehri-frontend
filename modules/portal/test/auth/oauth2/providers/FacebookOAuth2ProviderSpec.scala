package auth.oauth2.providers

import play.api.test.PlaySpecification

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
class FacebookOAuth2ProviderSpec extends PlaySpecification {

   val testAccessData = """access_token=some-access-token&expires=100"""

   val testUserData = """{"name":"Any Name","first_name":"Any","last_name":"Name","picture":{"data":{"is_silhouette":false,"url":"https:\/\/fbcdn-profile-a.akamaihd.net\/hprofile-ak-ash2\/v\/t1.0-1\/c14.14.172.172\/s50x50\/blah.jpg"}},"email":"email\u0040example.com","id":"123456789"}
   """

   "Facebook OAuth2 provider" should {
     "parse access data" in {
       FacebookOAuth2Provider.buildOAuth2Info(testAccessData) must beSome.which { d =>
         d.accessToken must equalTo("some-access-token")
         d.refreshToken must equalTo(None)
         d.expiresIn must equalTo(Some(100))
       }
     }

     "parse user data" in {
       FacebookOAuth2Provider.getUserData(testUserData) must beSome.which { d =>
         d.name must equalTo("Any Name")
         d.email must equalTo("email@example.com")
         d.providerId must equalTo("123456789")
       }
     }
   }
 }
