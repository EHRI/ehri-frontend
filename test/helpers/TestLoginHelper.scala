package helpers

import play.api.http.{MimeTypes, HeaderNames}
import play.api.test.FakeRequest
import play.api.GlobalSettings
import play.filters.csrf.CSRFFilter
import models.sql.SqlAccount
import mocks.{MockSearchDispatcher, userFixtures, MockSearchIndexer}
import global.GlobalConfig
import utils.search.{Indexer, Dispatcher}
import play.api.test.FakeApplication
import com.tzavellas.sse.guice.ScalaModule
import rest.RestEventHandler
import models.Account
import play.api.mvc.{RequestHeader, WithFilters}
import jp.t2v.lab.play2.auth.test.Helpers._
import controllers.base.Authorizer
import scala.concurrent.Future

/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestLoginHelper {

  val CSRF_TOKEN_NAME = "csrfToken"
  val CSRF_HEADER_NAME = "Csrf-Token"
  val CSRF_HEADER_NOCHECK = "nocheck"
  val fakeCsrfString = "fake-csrf-token"
  val testPassword = "testpass"

  val mockIndexer: MockSearchIndexer = new MockSearchIndexer()
  val mockDispatcher: MockSearchDispatcher = new MockSearchDispatcher()

  // More or less the same as run config but synchronous (so
  // we can validate the actions)
  // Note: this is defined as an implicit object here so it
  // can be used by the DAO classes directly.
  implicit object RestEventCollector extends RestEventHandler {
    def handleCreate(id: String) = mockIndexer.indexId(id)
    def handleUpdate(id: String) = mockIndexer.indexId(id)
    def handleDelete(id: String) = mockIndexer.clearId(id)
  }

  object TestConfig extends globalConfig.BaseConfiguration {
    val eventHandler = RestEventCollector
  }

  // Dummy auth config for play-2-auth
  object AuthConfig extends Authorizer {
    val globalConfig = TestConfig
  }


  /**
   * A Global object that loads fixtures on application start.
   */
  def getGlobal: GlobalSettings = new WithFilters(CSRFFilter()) with GlobalSettings {
    class TestModule extends ScalaModule {
      def configure() {
        bind[GlobalConfig].toInstance(TestConfig)
        bind[Indexer].toInstance(mockIndexer)
        bind[Dispatcher].toInstance(mockDispatcher)
      }
    }

    private lazy val injector = {
      com.google.inject.Guice.createInjector(new TestModule)
    }

    override def getControllerInstance[A](clazz: Class[A]) = {
      injector.getInstance(clazz)
    }

    override def onError(request: RequestHeader, ex: Throwable) = ex match {
      case e: rest.PermissionDenied => Future.successful(play.api.mvc.Results.Unauthorized("denied! No stairway!"))
      case e => super.onError(request, e)
    }

    override def onStart(app: play.api.Application) {
      // Workaround for issue #845
      app.routes
      super.onStart(app)
    }
  }

  /**
   * Get a FakeApplication with the given configuration, plus any plugins
   */
  def fakeApplication(additionalConfiguration: Map[String, Any] = Map(), global: GlobalSettings = getGlobal) = {
    FakeApplication(
      additionalConfiguration = additionalConfiguration ++ getConfig,
      additionalPlugins = getPlugins,
      withGlobal = Some(global)
    )
  }

  def getConfig = Map.empty[String,Any]

  /**
   * Get a set of plugins necessary to enable to desired login method.
   */
  def getPlugins: Seq[String] = Seq("mocks.MockBufferedMailerPlugin")

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   */
  def fakeLoggedInRequest(user: Account, rtype: String, path: String) = {
    val fr = FakeRequest(rtype, path)

    // Since we use csrf in forms, even though it's disabled in
    // tests we still need to add a fake token to the session so
    // the token is there when the form tries to render it.
    fr.withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
      .withHeaders(CSRF_HEADER_NAME -> CSRF_HEADER_NOCHECK)
      .withLoggedIn(AuthConfig)(user.id)
  }

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   */
  def fakeLoggedInHtmlRequest(user: Account, rtype: String, path: String) = {
    fakeLoggedInRequest(user, rtype, path)
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.HTML, HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)
  }

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   */
  def fakeLoggedInJsonRequest(user: Account, rtype: String, path: String) = {
    fakeLoggedInRequest(user, rtype, path)
      .withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
  }

}

/**
 * Implementation that uses various mocks to create the auth cookie.
 */
trait TestMockLoginHelper extends TestLoginHelper {

  override def getPlugins = super.getPlugins ++ Seq("models.sql.MockAccountDAO")
}

/**
 * Login helper that users the fixtures and creates the auth cookie via
 * a real login with a password.
 */
trait TestRealLoginHelper extends TestLoginHelper {

  /**
   * Global which loads fixtures on start
   */
  object FakeGlobal extends WithFilters(CSRFFilter()) with GlobalSettings {
    override def onStart(app: play.api.Application) = {
      // Initialize routes to fix #845
      app.routes

      // Initialize user fixtures
      userFixtures.values.map { user =>
        SqlAccount.findByProfileId(user.id) orElse SqlAccount.create(user.email, user.id).map { u =>
          u.setPassword(Account.hashPassword(testPassword))
        }
      }
    }
  }

  override def getGlobal = FakeGlobal
}