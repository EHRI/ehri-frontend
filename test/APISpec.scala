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
import models.Entity
import org.specs2.specification.BeforeExample
import defines.EntityType
import models.UserProfile
import play.api.GlobalSettings
import models.base.Accessor
import controllers.routes
import helpers.TestMockLoginHelper
import play.api.libs.json.Json
import play.api.http.HeaderNames
import controllers.base.AccessPointLink

/**
 * Spec for testing various JSON endpoints used by Ajax components etc.
 */
class APISpec extends Specification with BeforeExample with TestMockLoginHelper {
  sequential

  import mocks.UserFixtures.{privilegedUser,unprivilegedUser}

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)
  val userProfile = UserProfile(Entity.fromString(privilegedUser.profile_id, EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

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

  def before = {
    runner.tearDown
    runner.setUp
  }

  "Link JSON endpoints should" should {
    "allow creating and reading" in {
      running(fakeLoginApplication(additionalConfiguration = config)) {
        val json = Json.toJson(new AccessPointLink("ur1", "c1", "a1", description = Some("Test link")))
        val cr = route(fakeLoggedInRequest(privilegedUser, POST,
          routes.DocumentaryUnits.createLinkJson.url)
          .withHeaders(postHeaders.toSeq: _*), json).get
        status(cr) must equalTo(CREATED)
        val cr2 = route(fakeLoggedInRequest(privilegedUser, GET,
          routes.DocumentaryUnits.getLinkJson("c1", "ur1").url)).get
        status(cr2) must equalTo(OK)
        Json.parse(contentAsString(cr2)) mustEqual json
      }
    }
  }

  step {
    runner.stop
  }
}
