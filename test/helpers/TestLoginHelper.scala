package helpers

import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.http.HeaderNames
import play.api.test.Helpers._
import play.api.test.FakeApplication


trait TestLoginHelper {
  
	val LOGIN_PATH = "/login" // FIXME: Determine this from routes somehow
  
	def fakeLoginApplication: FakeApplication = fakeLoginApplication(Map())  
	
	def fakeLoginApplication(additionalConfiguration: Map[String,Any] = Map()) = FakeApplication(
			additionalConfiguration=additionalConfiguration,
			additionalPlugins = Seq("mocks.MockUserDAO", "mocks.MockLoginHandler")    
	)

	def fakeLoggedInRequest(rtype: String, path: String) = {
       val cookies = header(HeaderNames.SET_COOKIE, 
           route(play.api.test.FakeRequest(POST, LOGIN_PATH)).get)
        		.getOrElse(sys.error("No Authorization cookie found"))
       FakeRequest(rtype, path).withHeaders(HeaderNames.COOKIE -> cookies)
	}
}