package helpers

import controllers.routes
import play.api.http.HeaderNames
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import play.api.test.Helpers.header
import play.api.test.Helpers._
import play.api.GlobalSettings
import play.filters.csrf.{CSRFFilter, CSRF}

trait TestLoginHelper {


  object FakeGlobal extends GlobalSettings

  val LOGIN_PATH = routes.Application.login.url

  def fakeLoginApplication(userProfile: String, additionalConfiguration: Map[String, Any] = Map(), global: GlobalSettings = FakeGlobal) = {
    FakeApplication(
      additionalConfiguration = additionalConfiguration ++ Map("test.user.profile_id" -> userProfile),
      additionalPlugins = Seq("mocks.MockUserDAO", "mocks.MockLoginHandler"),
      withGlobal = Some(global)
    )
  }

  def fakeLoggedInRequest(rtype: String, path: String) = {
    // Login and add the auth cookie to the session.
    val cookies = header(HeaderNames.SET_COOKIE,
      route(play.api.test.FakeRequest(POST, LOGIN_PATH)).get)
      .getOrElse(sys.error("No Authorization cookie found"))
    val fr = FakeRequest(rtype, path).withHeaders(HeaderNames.COOKIE -> cookies)

    // Since we use csrf in forms, even though it's disabled in
    // tests we still need to add a fake token to the session so
    // the token is there when the form tries to render it.
    fr.withSession(CSRF.Conf.TOKEN_NAME -> "fake-csrf-token")
  }
}