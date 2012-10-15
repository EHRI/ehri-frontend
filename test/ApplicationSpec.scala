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

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification with BeforeExample {
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

  "Application" should {
    "send 404 on a bad request" in {
      running(FakeApplication(additionalConfiguration=config)) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "list should get json" in {
      running(FakeApplication(additionalConfiguration=config)) {
        val list = route(FakeRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Items")
      }
    }

    "show should get a wierd string" in {
      running(FakeApplication(additionalConfiguration=config)) {
        // FIXME: Set the the unit that is world-accessible because we
        // can't yet log in from tests...
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c4")).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c4")
      }
    }

    "show should permission denied" in {
      running(FakeApplication(additionalConfiguration=config)) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c1")).get
        status(show) must equalTo(UNAUTHORIZED)
      }
    }

    "render the index page" in {
      running(FakeApplication(additionalConfiguration=config)) {
        val home = route(FakeRequest(GET, "/")).get

        status(home) must equalTo(OK)
        contentType(home) must beSome.which(_ == "text/html")
        contentAsString(home) must contain("Your new application is ready.")
      }
    }
  }

  step {
    runner.stop
  }
}