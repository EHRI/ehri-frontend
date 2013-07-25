package helpers

import controllers.routes
import globalconfig.RunConfiguration
import play.api.http.{MimeTypes, HeaderNames}
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import play.api.test.Helpers.header
import play.api.test.Helpers._
import play.api.GlobalSettings
import play.filters.csrf.{CSRFFilter, CSRF}
import play.filters.csrf.CSRF.Token
import models.sql.{User, OpenIDUser}
import org.mindrot.jbcrypt.BCrypt
import mocks.{MockSearchIndexer, UserFixtures}
import models.json.Utils
import global.{MenuConfig, GlobalConfig}
import controllers.base.LoginHandler
import utils.search.{Indexer, Dispatcher}

/**
 * Mixin trait that provides some handy methods to test actions that
 * have authorisation, such as fakeApplication and fakeLoggedInHtmlRequest.
 */
trait TestLoginHelper {

  val fakeCsrfString = "fake-csrf-token"
  val testPassword = "testpass"

  object TestConfig extends GlobalConfig {
    val searchIndexer: Indexer = mocks.MockSearchIndexer()
    val searchDispatcher: Dispatcher = mocks.MockSearchDispatcher()
    implicit lazy val menuConfig: MenuConfig = RunConfiguration.menuConfig
    val loginHandler: LoginHandler = mocks.MockLoginHandler()
  }

  def getGlobal: GlobalSettings = {
    /**
     * A Global object that loads fixtures on application start.
     */
    object FakeGlobal extends CSRFFilter(() => Token(fakeCsrfString)) with GlobalSettings {

      import com.tzavellas.sse.guice.ScalaModule
      import utils.search.Dispatcher

      class TestModule extends ScalaModule {
        def configure() {
          bind[GlobalConfig].toInstance(TestConfig)
          bind[Indexer].toInstance(MockSearchIndexer())
        }
      }

      private lazy val injector = {
        com.google.inject.Guice.createInjector(new TestModule)
      }

      override def getControllerInstance[A](clazz: Class[A]) = {
        injector.getInstance(clazz)
      }

      override def onStart(app: play.api.Application) {
        // Workaround for issue #845
        app.routes
        Utils.registerModels
        super.onStart(app)
      }
    }
    FakeGlobal
  }

  /**
   * Get a FakeApplication with the given configuration, plus any plugins
   * @param additionalConfiguration
   * @param global
   * @return
   */
  def fakeApplication(additionalConfiguration: Map[String, Any] = Map(), global: GlobalSettings = getGlobal) = {
    FakeApplication(
      additionalConfiguration = additionalConfiguration ++ getConfig,
      additionalPlugins = getPlugins,
      withGlobal = Some(global)
    )
  }

  def getConfig = Map(
    "db.default.driver" -> "org.h2.Driver",
    "db.default.url" -> "jdbc:h2:mem:play",
    "db.default.user" -> "sa",
    "db.default.password" -> ""
  )

  /**
   * Get a set of plugins necessary to enable to desired login method.
   * @return
   */
  def getPlugins: Seq[String] = Seq()

  /**
   * Override this to cookies obtained via a specific method.
   * @param user
   * @return
   */
  def getAuthCookies(user: User): String

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   * @param user
   * @param rtype
   * @param path
   * @return
   */
  def fakeLoggedInRequest(user: User, rtype: String, path: String) = {
    val fr = FakeRequest(rtype, path).withHeaders(HeaderNames.COOKIE -> getAuthCookies(user))

    // Since we use csrf in forms, even though it's disabled in
    // tests we still need to add a fake token to the session so
    // the token is there when the form tries to render it.
    fr.withSession(CSRF.Conf.TOKEN_NAME -> fakeCsrfString)
  }

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   * @param user
   * @param rtype
   * @param path
   * @return
   */
  def fakeLoggedInHtmlRequest(user: User, rtype: String, path: String)
        = fakeLoggedInRequest(user, rtype, path)
            .withHeaders(HeaderNames.ACCEPT -> MimeTypes.HTML, HeaderNames.CONTENT_TYPE -> MimeTypes.FORM)

  /**
   * Get a FakeRequest with authorization cookies for the given user
   * and HTML Accept.
   * @param user
   * @param rtype
   * @param path
   * @return
   */
  def fakeLoggedInJsonRequest(user: User, rtype: String, path: String)
  = fakeLoggedInRequest(user, rtype, path).withHeaders(HeaderNames.ACCEPT -> MimeTypes.JSON)
}

/**
 * Implementation that uses various mocks to create the auth cookie.
 */
trait TestMockLoginHelper extends TestLoginHelper {

  override def getPlugins = Seq("mocks.MockUserDAO")

  /**
   * Get a user auth cookie using the Mock login mechanism, which depends
   * on the MockUserDAO and MockLoginHandler being enabled.
   * @param user
   * @return
   */
  def getAuthCookies(user: User): String = {
    header(HeaderNames.SET_COOKIE,
      route(play.api.test.FakeRequest(POST, controllers.core.routes.Application.login.url),
          Map("profile" -> Seq(user.profile_id))).get)
      .getOrElse(sys.error("No Authorization cookie found"))
  }
}

/**
 * Login helper that users the fixtures and creates the auth cookie via
 * a real login with a password.
 */
trait TestRealLoginHelper extends TestLoginHelper {

  /**
   * Global which loads fixtures on start
   */
  object FakeGlobal extends CSRFFilter(() => Token(fakeCsrfString)) with GlobalSettings {
    override def onStart(app: play.api.Application) = {
      // Initialize routes to fix #845
      app.routes

      // Initialize user fixtures
      UserFixtures.all.map { user =>
        OpenIDUser.findByProfileId(user.profile_id) orElse OpenIDUser.create(user.email, user.profile_id).map { u =>
          u.setPassword(BCrypt.hashpw(testPassword, BCrypt.gensalt()))
        }
      }
    }
  }

  override def getGlobal = FakeGlobal

  /**
   * Get a user auth cookie using the proper login method.
   * @param user
   * @return
   */
  def getAuthCookies(user: User): String = {
    // Login and add the auth cookie to the session.
    val loginData = Map(
      "email" -> Seq(user.email),
      "password" -> Seq(testPassword)
    )
    header(HeaderNames.SET_COOKIE,
      route(play.api.test.FakeRequest(POST, controllers.core.routes.Admin.passwordLoginPost.url), loginData).get)
        .getOrElse(sys.error("No Authorization cookie found"))
  }
}