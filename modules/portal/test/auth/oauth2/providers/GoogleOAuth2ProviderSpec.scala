package auth.oauth2.providers

import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class GoogleOAuth2ProviderSpec extends PlaySpecification {

  val testAccessData = """{
     "access_token" : "some-access-token",
     "token_type" : "Bearer",
     "expires_in" : 100,
     "id_token" : "some-id-token"
  }
  """

  val testUserData = """{
      "id": "some-id",
      "email": "email@example.com",
      "verified_email": true,
      "name": "Any Name",
      "given_name": "Any",
      "family_name": "Name",
      "link": "https://plus.google.com/123456789",
      "picture": "https://lh6.googleusercontent.com/blah/photo.jpg",
      "gender": "female",
      "locale": "en-GB"
  }
  """

  "Google OAuth2 provider" should {
    "parse access data" in {
      GoogleOAuth2Provider.buildOAuth2Info(testAccessData) must beSome.which { d =>
        d.accessToken must equalTo("some-access-token")
      }
    }

    "parse user data" in {
      GoogleOAuth2Provider.getUserData(testUserData) must beSome.which { d =>
        d.name must equalTo("Any Name")
        d.email must equalTo("email@example.com")
      }
    }
  }
}
