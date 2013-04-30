package test

import org.specs2.mutable._
import play.api.test._
import play.api.test.Helpers._
import org.neo4j.server.configuration.ThirdPartyJaxRsPackage
import eu.ehri.extension.test.utils.ServerRunner
import eu.ehri.extension.AbstractAccessibleEntityResource
import com.typesafe.config.ConfigFactory
import rest._
import play.api.libs.concurrent.Execution.Implicits._
import models.{AccessPointF, Entity, UserProfile}
import org.specs2.specification.BeforeExample
import defines.EntityType
import play.api.GlobalSettings
import models.base.Accessor
import controllers.routes
import helpers.TestMockLoginHelper
import play.api.libs.json.Json
import play.api.http.HeaderNames
import controllers.base.{NewAccessPointLink, AccessPointLink}

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends Specification with BeforeExample with TestMockLoginHelper {
  sequential

  import mocks.UserFixtures.{privilegedUser, unprivilegedUser}

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)

  val entityType = EntityType.UserProfile

  object FakeGlobal extends GlobalSettings

  val runner: ServerRunner = new ServerRunner(classOf[DAOSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(
    classOf[AbstractAccessibleEntityResource[_]].getPackage.getName, "/ehri"));
  runner.start

  val postHeaders: Map[String, String] = Map(
    HeaderNames.CONTENT_TYPE -> "application/json"
  )

  class FakeApp extends WithApplication(fakeApplication(additionalConfiguration = config))

  def before = {
    runner.tearDown
    runner.setUp
  }

  "Link JSON endpoints should" should {
    "allow creating and reading" in new FakeApp {
      val json = Json.toJson(new AccessPointLink("a1", description = Some("Test link")))
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createLinkJson("c1", "ur1").url)
        .withHeaders(postHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      val cr2 = route(fakeLoggedInRequest(privilegedUser, GET,
        routes.DocumentaryUnits.getLinkJson("c1", "ur1").url)).get
      status(cr2) must equalTo(OK)
      Json.parse(contentAsString(cr2)) mustEqual json
    }

    "allow creating new access points along with a link" in new FakeApp {
      val link = new AccessPointLink("a1", description = Some("Test link"))
      val apdata = new NewAccessPointLink("Test Access Point", AccessPointF.AccessPointType.SubjectAccess, link)
      val json = Json.toJson(apdata)
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createAccessPointLinkJson("c1", "cd1").url)
        .withHeaders(postHeaders.toSeq: _*), json).get
      status(cr) must equalTo(CREATED)
      println(contentAsString(cr))
    }
  }

  step {
    runner.stop
  }
}
