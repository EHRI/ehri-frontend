package helpers

import auth.oauth2.{MockOAuth2Flow, OAuth2Flow}
import auth.{AccountManager, MockAccountManager}
import backend.HelpdeskDAO.HelpdeskResponse
import backend._
import backend.helpdesk.{MockFeedbackDAO, MockHelpdeskDAO}
import backend.rest.{CypherIdGenerator, RestBackend}
import com.google.inject.{AbstractModule, Guice}
import com.typesafe.plugin.MailerAPI
import controllers.base.AuthConfigImpl
import global.GlobalConfig
import jp.t2v.lab.play2.auth.test.Helpers._
import mocks.{MockBufferedMailer, _}
import models.{Account, Feedback}
import play.api.GlobalSettings
import play.api.http.{HeaderNames, MimeTypes}
import play.api.mvc.{RequestHeader, WithFilters}
import play.api.test.{FakeApplication, FakeRequest}
import play.filters.csrf.CSRFFilter
import utils.search.{MockSearchDispatcher, MockSearchIndexer, _}

import scala.concurrent.Future

/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestConfiguration {

  import play.api.Play.current

  // Stateful buffers for capturing stuff like feedback, search
  // parameters, and reset tokens. These persist across tests in
  // a very unclean way but are useful for determining the last-used
  // whatsit etc...
  val feedbackBuffer = collection.mutable.HashMap.empty[Int,Feedback]
  val helpdeskBuffer = collection.mutable.HashMap.empty[Int, Seq[HelpdeskResponse]]
  val mailBuffer = collection.mutable.ListBuffer.empty[MockMail]
  val searchParamBuffer = collection.mutable.ListBuffer.empty[ParamLog]
  val indexEventBuffer = collection.mutable.ListBuffer.empty[String]

  // Might want to mock the backend at at some point!
  def testBackend: Backend = new RestBackend(testEventHandler)

  def mockResolver: MockSearchResolver = new MockSearchResolver
  def idGenerator: IdGenerator = new CypherIdGenerator("%06d")
  def mockDispatcher: Dispatcher = new MockSearchDispatcher(testBackend, searchParamBuffer)
  def mockFeedback: FeedbackDAO = new MockFeedbackDAO(feedbackBuffer)
  def mockHelpdesk: HelpdeskDAO = new MockHelpdeskDAO(helpdeskBuffer)
  def mockMailer: MailerAPI = new MockBufferedMailer(mailBuffer)
  def mockIndexer: Indexer = new MockSearchIndexer(indexEventBuffer)
  // NB: The mutable state for the user DAO is still stored globally
  // in the mocks package.
  def mockAccounts: AccountManager = MockAccountManager()
  def oAuth2Flow: OAuth2Flow = MockOAuth2Flow()

  // More or less the same as run config but synchronous (so
  // we can validate the actions)
  // Note: this is defined as an implicit object here so it
  // can be used by the DAO classes directly.
  val testEventHandler = new EventHandler {
    def handleCreate(id: String) = mockIndexer.indexId(id)
    def handleUpdate(id: String) = mockIndexer.indexId(id)
    def handleDelete(id: String) = mockIndexer.clearId(id)
  }

  object TestConfig extends GlobalConfig

  // Dummy auth config for play-2-auth
  object AuthConfig extends AuthConfigImpl {
    val globalConfig = TestConfig
    val accounts = mockAccounts
  }

  /**
   * A Global object that loads fixtures on application start.
   */
  def getGlobal: GlobalSettings = new WithFilters(CSRFFilter()) with GlobalSettings {
    lazy val injector = Guice.createInjector(new AbstractModule {
      protected def configure() {
        bind(classOf[GlobalConfig]).toInstance(TestConfig)
        bind(classOf[Indexer]).toInstance(mockIndexer)
        bind(classOf[Backend]).toInstance(testBackend)
        bind(classOf[Dispatcher]).toInstance(mockDispatcher)
        bind(classOf[Resolver]).toInstance(mockResolver)
        bind(classOf[FeedbackDAO]).toInstance(mockFeedback)
        bind(classOf[HelpdeskDAO]).toInstance(mockHelpdesk)
        bind(classOf[IdGenerator]).toInstance(idGenerator)
        bind(classOf[MailerAPI]).toInstance(mockMailer)
        bind(classOf[AccountManager]).toInstance(mockAccounts)
        bind(classOf[OAuth2Flow]).toInstance(oAuth2Flow)
      }
    })

    override def getControllerInstance[A](clazz: Class[A]) = {
      injector.getInstance(clazz)
    }

    override def onError(request: RequestHeader, ex: Throwable) = ex match {
      case e: backend.rest.PermissionDenied => Future.successful(play.api.mvc.Results.Unauthorized("denied! No stairway!"))
      case e => super.onError(request, e)
    }
  }

  val CSRF_TOKEN_NAME = "csrfToken"
  val CSRF_HEADER_NAME = "Csrf-Token"
  val CSRF_HEADER_NOCHECK = "nocheck"
  val fakeCsrfString = "fake-csrf-token"
  val testPassword = "testpass"

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
   * Get a FakeRequest with CSRF Headers
   */
  def fakeRequest(rtype: String, path: String) = {
    val fr = FakeRequest(rtype, path)
    // Since we use csrf in forms, even though it's disabled in
    // tests we still need to add a fake token to the session so
    // the token is there when the form tries to render it.
    fr.withSession(CSRF_TOKEN_NAME -> fakeCsrfString)
      .withHeaders(CSRF_HEADER_NAME -> CSRF_HEADER_NOCHECK)
  }

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   */
  def fakeLoggedInRequest(user: Account, rtype: String, path: String) = {
    fakeRequest(rtype, path)
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