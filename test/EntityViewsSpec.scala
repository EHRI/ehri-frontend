package test

import org.junit.runner.RunWith
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import org.specs2.specification.BeforeExample

import eu.ehri.plugin.test.utils.ServerRunner
import eu.ehri.extension.EhriNeo4jFramedResource

import helpers.TestLoginHelper
import play.api.test.FakeApplication
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.test.Helpers.contentAsString
import play.api.test.Helpers.route
import play.api.test.Helpers.running
import play.api.test.Helpers.status

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class EntityViewsSpec extends Specification with BeforeExample with TestLoginHelper {
  sequential

  val testPrivilegedUser = "mike"
  val testOrdinaryUser = "reto"

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
    "list should get some (world-readable) items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val list = route(FakeRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Items")

        contentAsString(list) must not contain ("c1")
        contentAsString(list) must contain("c4")

      }
    }

    "list when logged in should get more items" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val list = route(fakeLoggedInRequest(GET, "/documentaryUnit/list")).get
        status(list) must equalTo(OK)
        contentAsString(list) must contain("Items")
        contentAsString(list) must contain("c1")
        contentAsString(list) must contain("c2")
        contentAsString(list) must contain("c3")
        contentAsString(list) must contain("c4")
      }
    }

    "give access to c1 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, "/documentaryUnit/show/c1")).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c1")
      }
    }

    "deny access to c1 when logged in as an ordinary user" in {
      running(fakeLoginApplication(testOrdinaryUser, additionalConfiguration = config)) {
        val show = route(fakeLoggedInRequest(GET, "/documentaryUnit/show/c2")).get
        status(show) must equalTo(UNAUTHORIZED)
        contentAsString(show) must not contain ("c2")
      }
    }

    "allow access to c4 by default" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c4")).get
        status(show) must equalTo(OK)
        contentAsString(show) must contain("c4")
      }
    }

    "allow deleting c4 when logged in" in {
      running(fakeLoginApplication(testPrivilegedUser, additionalConfiguration = config)) {
        // get the number of items before...
        val list = route(fakeLoggedInRequest(POST, "/documentaryUnit/delete/c4")).get
        println(status(list))
        status(list) must equalTo(SEE_OTHER)
      }
    }

    "should redirect to login page when permission denied when not logged in" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val show = route(FakeRequest(GET, "/documentaryUnit/show/c1")).get
        status(show) must equalTo(SEE_OTHER)
      }
    }
  }

  step {
    runner.stop
  }
}