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

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
  sequential

  // Load the test server using a different config...
  val config = ConfigFactory.load("test.conf")
  val port = config.getInt("neo4j.server.port")
  val endpoint = config.getString("neo4j.server.endpoint")

  val runner: ServerRunner = new ServerRunner(classOf[ApplicationSpec].getName, port)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/" + endpoint));
  runner.start();

  "Application" should {
    "send 404 on a bad request" in {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "list should get json" in {
      running(FakeApplication()) {
        val list = route(FakeRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
      }
    }

    "show should get a wierd string" in {
      running(FakeApplication()) {
        // FIXME: Set the the unit that is world-accessible because we
        // can't yet log in from tests...
        val show = route(FakeRequest(GET, "/documentaryUnit/show/7")).get
        status(show) must equalTo(OK)
        contentAsString(show) must equalTo("<Some(\"c4\") (7)>")
      }
    }

    "render the index page" in {
      running(FakeApplication()) {
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