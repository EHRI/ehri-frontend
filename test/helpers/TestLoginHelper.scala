package helpers

import controllers.routes
import play.api.http.HeaderNames
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import play.api.test.Helpers.header
import play.api.test.Helpers.route

trait TestLoginHelper {

  val LOGIN_PATH = routes.Application.login.url

  def fakeLoginApplication(userProfile: String): FakeApplication = fakeLoginApplication(userProfile, Map())

  def fakeLoginApplication(userProfile: String, additionalConfiguration: Map[String, Any] = Map()) = {
    FakeApplication(
      additionalConfiguration = additionalConfiguration ++ Map("test.user.profile_id" -> userProfile),
      additionalPlugins = Seq("mocks.MockUserDAO", "mocks.MockLoginHandler")
    )
  }

  def fakeLoggedInRequest(rtype: String, path: String) = {
    val cookies = header(HeaderNames.SET_COOKIE,
      route(play.api.test.FakeRequest(POST, LOGIN_PATH)).get)
      .getOrElse(sys.error("No Authorization cookie found"))
    FakeRequest(rtype, path).withHeaders(HeaderNames.COOKIE -> cookies)
  }
}