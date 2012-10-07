package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import eu.ehri.plugin.test.utils.ServerRunner
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner
import eu.ehri.extension.EhriNeo4jFramedResource

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {
  sequential

  "Application" should {

    var runner: ServerRunner = null
    step {
      runner = new ServerRunner(classOf[ApplicationSpec].getName, 7575)
      runner.getConfigurator
        .getThirdpartyJaxRsClasses()
        .add(new ThirdPartyJaxRsPackage(classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
      println("Starting server...")
      runner.start();
    }

    "send 404 on a bad request" in {
      running(FakeApplication()) {
        route(FakeRequest(GET, "/boum")) must beNone
      }
    }

    "list should get json" in {
      running(FakeApplication()) {
        val list = route(FakeRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
        contentType(list) must beSome.which(_ == "application/json")
      }
    }

    "show should get a wierd string" in {
      running(FakeApplication()) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/1")).get
        status(show) must equalTo(OK)
        contentAsString(show) must equalTo("<Some(\"c1\") (1)>")
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

    step {
      println("Stopping server...")
      runner.stop
    }

  }
}