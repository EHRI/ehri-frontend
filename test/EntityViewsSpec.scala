package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import eu.ehri.plugin.test.utils.ServerRunner
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import eu.ehri.extension.EhriNeo4jFramedResource
import com.typesafe.config.ConfigFactory
import org.specs2.specification.BeforeExample
import helpers.TestLoginHelper

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class EntityViewsSpec extends Specification with BeforeExample with TestLoginHelper {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  val runner: ServerRunner = new ServerRunner(classOf[ApplicationSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
  runner.start

  def before = {
    runner.tearDown
    runner.setUp
  }

  "Entity views" should {
    "list should get json" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val list = route(FakeRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Items")
      }
    }

    "allow access to world-readable items by default" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c4")).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c4")
      }
    }

    "give access to c1 when logged in" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, "/documentaryUnit/show/c1")).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c1")
      }
    }

    "show should permission denied when not logged in" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c1")).get
        status(show) must equalTo(UNAUTHORIZED)
      }
    }
  }

  step {
    runner.stop
  }
}