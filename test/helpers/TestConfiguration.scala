package helpers

import play.api.http.{MimeTypes, HeaderNames}
import play.api.test.{FakeApplication, FakeRequest}
import play.api.GlobalSettings
import play.filters.csrf.CSRFFilter
import models.MockAccountDAO
import mocks._
import global.GlobalConfig
import utils.search._
import com.tzavellas.sse.guice.ScalaModule
import models.{AccountDAO, Account}
import play.api.mvc.{RequestHeader, WithFilters}
import jp.t2v.lab.play2.auth.test.Helpers._
import controllers.base.AuthConfigImpl
import scala.concurrent.Future
import backend._
import backend.rest.{CypherIdGenerator, RestBackend}
import utils.search._
import backend.rest.RestBackend
import utils.search.MockSearchResolver
import backend.rest.RestBackend
import scala.Some
import backend.rest.CypherIdGenerator
import utils.search.MockSearchIndexer
import play.api.test.FakeApplication
import utils.search.MockSearchDispatcher
import com.typesafe.plugin.MailerAPI

/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestConfiguration {

  val CSRF_TOKEN_NAME = "csrfToken"
  val CSRF_HEADER_NAME = "Csrf-Token"
  val CSRF_HEADER_NOCHECK = "nocheck"
  val fakeCsrfString = "fake-csrf-token"
  val testPassword = "testpass"

  val mockIndexer: MockSearchIndexer = new MockSearchIndexer()
  // More or less the same as run config but synchronous (so
  // we can validate the actions)
  // Note: this is defined as an implicit object here so it
  // can be used by the DAO classes directly.
  val testEventHandler = new EventHandler {
    def handleCreate(id: String) = mockIndexer.indexId(id)
    def handleUpdate(id: String) = mockIndexer.indexId(id)
    def handleDelete(id: String) = mockIndexer.clearId(id)
  }

  // Might want to mock this at some point!
  val testBackend: Backend = new RestBackend(testEventHandler)

  val mockDispatcher: MockSearchDispatcher = new MockSearchDispatcher(testBackend)
  val mockResolver: MockSearchResolver = new MockSearchResolver
  val mockFeedback: MockFeedbackDAO = new MockFeedbackDAO
  val idGenerator: IdGenerator = new CypherIdGenerator("%06d")
  val mockUserDAO: AccountDAO = MockAccountDAO
  val mockMailer: MockBufferedMailer = new MockBufferedMailer


  object TestConfig extends globalConfig.BaseConfiguration

  // Dummy auth config for play-2-auth
  object AuthConfig extends AuthConfigImpl {
    val globalConfig = TestConfig
    val userDAO = mockUserDAO
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
        bind[Resolver].toInstance(mockResolver)
        bind[Backend].toInstance(testBackend)
        bind[FeedbackDAO].toInstance(mockFeedback)
        bind[IdGenerator].toInstance(idGenerator)
        bind[MailerAPI].toInstance(mockMailer)
        bind[AccountDAO].toInstance(mockUserDAO)
      }
    }

    private lazy val injector = {
      com.google.inject.Guice.createInjector(new TestModule)
    }

    override def getControllerInstance[A](clazz: Class[A]) = {
      injector.getInstance(clazz)
    }

    override def onError(request: RequestHeader, ex: Throwable) = ex match {
      case e: backend.rest.PermissionDenied => Future.successful(play.api.mvc.Results.Unauthorized("denied! No stairway!"))
      case e => super.onError(request, e)
    }
  }

  /**
   * Get a FakeApplication with the given configuration, plus any plugins
   */
  def fakeApplication(additionalConfiguration: Map[String, Any] = Map(), global: => GlobalSettings = getGlobal) = {
    FakeApplication(
      additionalConfiguration = getConfig ++ additionalConfiguration,
      additionalPlugins = getPlugins,
      withGlobal = Some(global)
    )
  }

  def getConfig = Map.empty[String,Any]

  def getPlugins = Seq.empty[String]

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