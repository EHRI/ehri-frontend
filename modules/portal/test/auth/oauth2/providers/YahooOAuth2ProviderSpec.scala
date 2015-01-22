package auth.oauth2.providers

import play.api.test.PlaySpecification

/**
  * @author Mike Bryant (http://github.com/mikesname)
  */
class YahooOAuth2ProviderSpec extends PlaySpecification {

   val testAccessData = """{"access_token":"some-access-token","token_type":"bearer","expires_in":100,"refresh_token":"blah","xoauth_yahoo_guid":"123456789"}"""

   val testUserData = """{"profile":{"guid":"123456789","addresses":[{"city":"","country":"UK","current":true,"id":1,"postalCode":"123456","state":"","street":"","type":"HOME"},{"city":"","country":"UK","current":true,"id":2,"postalCode":"","state":"","street":"","type":"WORK"}],"ageCategory":"A","birthYear":1900,"birthdate":"01/01","created":"2015-01-01T00:00:00Z","displayAge":100,"emails":[{"handle":"email2@example.com","id":10,"type":"HOME"},{"handle":"email@example.com","id":1,"primary":true,"type":"HOME"}],"familyName":"Name","gender":"F","givenName":"Any","image":{"height":192,"imageUrl":"https://s.yimg.com/dh/ap/social/profile/blah.png","size":"192x192","width":192},"intl":"uk","jurisdiction":"uk","lang":"en-GB","memberSince":"2012-01-01T00:00:00Z","migrationSource":1,"nickname":"Mike","notStored":true,"nux":"0","phones":[{"id":10,"number":"44-0123456789","type":"MOBILE"}],"profileMode":"PUBLIC","profileStatus":"ACTIVE","profileUrl":"http://profile.yahoo.com/blah","timeZone":"Europe/London","isConnected":true,"profileHidden":false,"profilePermission":"PRIVATE","uri":"https://social.yahooapis.com/v1/user/blah/profile","cache":false}}
   """

   "Yahoo OAuth2 provider" should {
     "parse access data" in {
       YahooOAuth2Provider.buildOAuth2Info(testAccessData) must beSome.which { d =>
         d.accessToken must equalTo("some-access-token")
         d.userGuid must equalTo(Some("123456789"))
         d.refreshToken must equalTo(Some("blah"))
         d.expiresIn must equalTo(Some(100))
         d.tokenType must equalTo(Some("bearer"))
       }
     }

     "parse user data" in {
       YahooOAuth2Provider.getUserData(testUserData) must beSome.which { d =>
         d.name must equalTo("Any Name")
         d.email must equalTo("email@example.com")
         d.providerId must equalTo("123456789")
       }
     }
   }
 }
