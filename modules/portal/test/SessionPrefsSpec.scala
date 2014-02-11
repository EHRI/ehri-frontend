package utils

import play.api.mvc._
import play.api.test.{FakeApplication, WithApplication, FakeRequest, PlaySpecification}
import scala.concurrent.Future
import play.api.libs.json.Json

// NB These config keys are needed at the moment in order to use
// the FakeApplication because we use the Mailer plugin, which
// depends on smtp.host, and crypto (for session reading/writing)
// which needs a dummy secret.
case class FakeApp() extends WithApplication(new FakeApplication(
  additionalConfiguration = Map("smtp.host" -> "localhost", "application.secret" -> "foobar")))

trait PrefTest {
  this: Controller =>

  import utils.SessionPrefs._

  def testGetPrefs() = Action { implicit request =>
    val prefs = request.preferences
    Ok(prefs.toString)
  }

  def testSavePrefs(langs: Seq[String]) = Action { implicit request =>
    val prefs = request.preferences.copy(
      defaultLanguages = Some(langs))
    Ok(langs.toString).withPreferences(prefs)
  }
}

/**
 * Test session loading/saving behaviour.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
class SessionPrefsSpec extends PlaySpecification with Results {
  class PrefTestController() extends Controller with PrefTest
  val default = new SessionPrefs

  "PrefTest#testGetPrefs" should {
    "show the default" in {
      val controller = new PrefTestController()
      val result: Future[SimpleResult] = controller.testGetPrefs().apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      bodyText must be equalTo default.toString
    }

    "update correctly" in new FakeApp {
      val langs = Seq("eng")
      val controller = new PrefTestController()
      val result: Future[SimpleResult] = controller.testSavePrefs(langs).apply(FakeRequest())
      val bodyText: String = contentAsString(result)
      session(result).get(SessionPrefs.STORE_KEY) must beSome.which { str =>
        val prefs = Json.parse(str).as[SessionPrefs]
        prefs.defaultLanguages must equalTo(Some(langs))
      }
    }
  }
}
