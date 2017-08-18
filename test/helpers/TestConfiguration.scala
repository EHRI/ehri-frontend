package helpers

import java.net.URI
import java.nio.file.Paths

import akka.stream.Materializer
import auth.oauth2.{MockOAuth2Flow, OAuth2Flow}
import auth._
import auth.handler.cookie.CookieIdContainer
import auth.handler.{AuthHandler, AuthIdContainer}
import services._
import services.feedback.{FeedbackService, MockFeedbackService}
import controllers.base.SessionPreferences
import models.{Account, CypherQuery, Feedback}
import org.specs2.execute.{AsResult, Result}
import play.api.{Application, Configuration}
import play.api.http.{Status, Writeable}
import play.api.i18n.{Lang, MessagesApi}
import play.api.inject.guice.{GuiceApplicationBuilder, GuiceApplicationLoader}
import play.api.libs.json.{Json, Writes}
import play.api.libs.mailer.{Email, MailerClient}
import play.api.libs.ws.WSClient
import play.api.mvc.request.{Cell, RequestAttrKey}
import play.api.mvc.{AnyContentAsFormUrlEncoded, Request, Session}
import play.api.test.Helpers._
import play.api.test._
import services.accounts.{AccountManager, MockAccountManager}
import services.cypher.{CypherQueryService, MockCypherQueryService}
import services.data.{IdSearchResolver, _}
import services.htmlpages.{HtmlPages, MockHtmlPages}
import services.redirects.{MockMovedPageLookup, MovedPageLookup}
import utils.MockBufferedMailer
import services.search.{MockSearchIndexMediator, _}
import services.storage.{FileStorage, MockFileStorage}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.{Await, ExecutionContext, Future}


/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestConfiguration {

  this: PlayRunners with RouteInvokers =>

  // Stateful buffers for capturing stuff like feedback, search
  // parameters, and reset tokens. These persist across tests in
  // a very unclean way but are useful for determining the last-used
  // whatsit etc...
  protected val feedbackBuffer: mutable.HashMap[Int, Feedback] = collection.mutable.HashMap.empty[Int,Feedback]
  protected val cypherQueryBuffer: mutable.HashMap[Int, CypherQuery] = collection.mutable.HashMap.empty[Int,CypherQuery]
  protected val mailBuffer: ListBuffer[Email] = collection.mutable.ListBuffer.empty[Email]
  protected val storedFileBuffer: ListBuffer[URI] = collection.mutable.ListBuffer.empty[java.net.URI]
  protected val searchParamBuffer: ListBuffer[ParamLog] = collection.mutable.ListBuffer.empty[ParamLog]
  protected val indexEventBuffer: ListBuffer[String] = collection.mutable.ListBuffer.empty[String]
  protected val movedPages: collection.mutable.ListBuffer[(String, String)] = collection.mutable.ListBuffer.empty[(String, String)]

  private def mockMailer: MailerClient = MockBufferedMailer(mailBuffer)
  private def mockIndexer: SearchIndexMediator = MockSearchIndexMediator(indexEventBuffer)
  private def mockFeedback: FeedbackService = MockFeedbackService(feedbackBuffer)
  private def mockCypherQueries: CypherQueryService = MockCypherQueryService(cypherQueryBuffer)

  // NB: The mutable state for the user DAO is still stored globally
  // in the mocks package.
  protected def mockAccounts: AccountManager = MockAccountManager(ExecutionContext.Implicits.global)
  private def mockOAuth2Flow: OAuth2Flow = MockOAuth2Flow()
  private def mockRelocator: MovedPageLookup = MockMovedPageLookup(movedPages)
  private def mockFileStorage: FileStorage = MockFileStorage(storedFileBuffer)
  private def mockHtmlPages: HtmlPages = MockHtmlPages()

  // More or less the same as run config but synchronous (so
  // we can validate the actions)
  // Note: this is defined as an implicit object here so it
  // can be used by the DAO classes directly.
  protected val testEventHandler = new EventHandler {
    def handleCreate(id: String): Unit = mockIndexer.handle.indexIds(id)
    def handleUpdate(id: String): Unit = mockIndexer.handle.indexIds(id)
    def handleDelete(id: String): Unit = mockIndexer.handle.clearIds(id)
  }

  protected val searchLogger = new SearchLogger {
    override def log(params: ParamLog): Unit = searchParamBuffer += params
  }

  import play.api.inject.bind

  protected val appBuilder: GuiceApplicationBuilder = new play.api.inject.guice.GuiceApplicationBuilder()
    .overrides(
      // since we run some concurrent requests as the same user its
      // important not to use the CacheIdContainer, since each new
      // login evicts the last.
      bind[AuthIdContainer].to(classOf[CookieIdContainer]),
      bind[MailerClient].toInstance(mockMailer),
      bind[OAuth2Flow].toInstance(mockOAuth2Flow),
      bind[FileStorage].toInstance(mockFileStorage),
      bind[MovedPageLookup].toInstance(mockRelocator),
      bind[AccountManager].toInstance(mockAccounts),
      bind[SearchEngine].to[MockSearchEngine],
      bind[FeedbackService].toInstance(mockFeedback),
      bind[CypherQueryService].toInstance(mockCypherQueries),
      bind[EventHandler].toInstance(testEventHandler),
      bind[DataApi].to[DataApiService],
      bind[SearchIndexMediator].toInstance(mockIndexer),
      bind[HtmlPages].toInstance(mockHtmlPages),
      // NB: Graph IDs are not stable during testing due to
      // DB churn, so using the String ID resolver rather than
      // the more efficient GID one used in production
      bind[SearchItemResolver].to[IdSearchResolver]
    ).bindings(
      bind[SearchLogger].toInstance(searchLogger)
    )

  // Might want to mock the dataApi at at some point!
  protected def dataApi(implicit app: play.api.Application, apiUser: ApiUser, executionContext: ExecutionContext): DataApiHandle =
    app.injector.instanceOf[DataApi].withContext(apiUser)(executionContext)

  protected val AUTH_TEST_HEADER_NAME = "PLAY2_AUTH_TEST_TOKEN"
  protected val CSRF_TOKEN_NAME = "csrfToken"
  protected val fakeCsrfString = "fake-csrf-token"
  protected val testPassword = "testpass"

  /**
   * Override this value for configuration common to
   * an entire class of specs.
   */
  protected def getConfig = Map.empty[String,Any]

  /**
    * Access an i18n message with default lang.
    */
  protected def message(key: String, args: Any*)(implicit messagesApi: MessagesApi, lang: Lang = Lang.defaultLang): String =
    messagesApi(key, args: _*)(lang)

  private def loadFixtures(f: () => Result)(implicit app: Application, ex: ExecutionContext): Future[Result] = {
    val config = app.injector.instanceOf[Configuration]
    val fixtures = Paths.get(this.getClass.getClassLoader.getResource("testdata.yaml").toURI).toFile
    val ws = app.injector.instanceOf[WSClient]
    val url = s"${utils.serviceBaseUrl("ehridata", config)}/tools/__INITIALISE"

    import org.specs2.execute.{Error, Failure}
    // Integration tests assume a server running locally. We then use the
    // initialise endpoint to clean it before each individual test.
    ws.url(url).post(fixtures).map(_.status).map {
        case Status.NO_CONTENT => try f() catch { case e: Throwable => Error(e) }
        case s => Failure(s"Unable to initialise test DB, got a status of: $s")
      } recover {
        case e => Failure(s"Unable to initialise test DB, got exception: ${e.getMessage}")
      }
  }

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  protected abstract class ITestApp(val specificConfig: Map[String,Any] = Map.empty) extends WithApplicationLoader(
    new GuiceApplicationLoader(appBuilder.configure(getConfig ++ specificConfig))) with Injecting {
    implicit def implicitMaterializer: Materializer = inject[Materializer]
    implicit def implicitExecContext: ExecutionContext = app.injector.instanceOf[ExecutionContext]
    implicit def messagesApi: MessagesApi = inject[MessagesApi]

    override def around[T: AsResult](t: => T): Result =
      await(loadFixtures(() => super.around(t)))
  }

  /**
    * Same as ITestApp but running a server.
    * @param app the app to use
    * @param port the server port
    */
  protected abstract class ITestServer(app: Application = GuiceApplicationBuilder().build(),
    port: Int = Helpers.testServerPort) extends WithServer(app, port) with Injecting {
    implicit def implicitExecContext: ExecutionContext = inject[ExecutionContext]

    override def around[T: AsResult](t: => T): Result =
      await(loadFixtures(() => super.around(t)))
  }

  /**
   * Test running Fake Application. We have general all-test configuration,
   * handled in `config`, and per-test configuration (`specificConfig`) that
   * will be merged.
   * @param specificConfig A map of config values for this test
   */
  protected abstract class DBTestApp(resource: String, specificConfig: Map[String,Any] = Map.empty) extends WithSqlFile(
    resource)(new GuiceApplicationLoader(appBuilder.configure(getConfig ++ specificConfig))) {
    implicit def messagesApi: MessagesApi = app.injector.instanceOf[MessagesApi]
  }

  /**
   * Run a spec after loading the given resource name as SQL fixtures.
   */
  protected abstract class WithSqlFile(val resource: String)(implicit appLoader: play.api.ApplicationLoader)
    extends WithApplicationLoader(appLoader) {
    override def around[T: AsResult](t: => T): Result = {
      running(app) {
        withDatabaseFixture(resource) { implicit db =>
          AsResult.effectively(t)
        }
      }
    }
  }

  /**
    * Generate a test authentication token for the given user ID.
    */
  protected def testAuthToken(id: String)(implicit app: Application): String = {
    import scala.concurrent.duration._
    val handler: AuthHandler = app.injector.instanceOf[AuthHandler]
    Await.result(handler.newSession(id), 10.seconds)
  }

  /**
   * Convenience extensions for the FakeRequest object.
   */
  protected implicit class RequestExtensions[A](fr: Request[A]) {

    def withLoggedIn(implicit app: Application): String => Request[A] = { id =>
      fr.withHeaders(fr.headers.add(AUTH_TEST_HEADER_NAME -> testAuthToken(id)))
    }

    /**
     * Set the request to be authenticated for the given user.
     */
    def withUser(user: Account)(implicit app: play.api.Application): Request[A] = {
      fr.withLoggedIn(app)(user.id)
    }

    def withSession(s: (String, String)*): Request[A] = {
      val newSession = Session(fr.session.data ++ s)
      fr.withAttrs(fr.attrs.updated(RequestAttrKey.Session, Cell(newSession)))
    }

    def withFormUrlEncodedBody(data: (String, String)*): Request[AnyContentAsFormUrlEncoded] = {
      fr.withBody(body = AnyContentAsFormUrlEncoded(play.utils.OrderPreserving.groupBy(data.toSeq)(_._1)))
    }

    /**
     * Add a dummy CSRF to the fake request.
     */
    def withCsrf: Request[A] = CSRFTokenHelper.addCSRFToken(fr)

    /**
     * Add a serialized preferences object to the fake request's session.
     */
    def withPreferences[T: Writes](p: T): Request[A] =
      fr.withSession(SessionPreferences.DEFAULT_STORE_KEY -> Json.stringify(Json.toJson(p)(implicitly[Writes[T]])))

    /**
     * Set the accepting header to the given mime-types.
     */
    def accepting(m: String*): Request[A] = m.foldLeft(fr) { (c, m) =>
      c.withHeaders(fr.headers.add(ACCEPT -> m))
    }

    /**
     * Call the request.
     */
    def call()(implicit app: play.api.Application, w: Writeable[A]): Future[play.api.mvc.Result] =
      route(app, fr).getOrElse(sys.error(s"Unexpected null route for ${fr.uri} (${fr.method})"))

    /**
     * Call the request with the given body.
     */
    def callWith[T](body: T)(implicit app: play.api.Application, w: Writeable[T]): Future[play.api.mvc.Result] =
      route(app, fr, body).getOrElse(sys.error(s"Unexpected null route for ${fr.uri} (${fr.method})"))
  }
}