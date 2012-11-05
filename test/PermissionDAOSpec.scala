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
import rest._
import play.api.libs.concurrent.execution.defaultContext
import models.Entity
import models.UserProfile
import play.api.libs.json.JsString
import org.specs2.specification.BeforeExample
import helpers.TestLoginHelper
import models.Group
import defines.EntityType
import defines.PermissionType

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class PermissionDAOSpec extends Specification with BeforeExample with TestLoginHelper {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)
  val userProfile = UserProfile(Some(-1L), "mike", "Mike", List(Group(Some(-2L), "admin", "Admin")))
  val entityType = EntityType.UserProfile

  val runner: ServerRunner = new ServerRunner(classOf[PermissionDAOSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(
      classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
  runner.start

  def before = {
    runner.tearDown
    runner.setUp
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val perms = await(PermissionDAO[UserProfile](userProfile).get)
        perms must beRight
        perms.right.get.get(EntityType.DocumentaryUnit, PermissionType.Create) must beSome
      }
    }
    
    "be able to set a user's permissions" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val user = UserProfile(Some(-2L), "reto", "Reto")
        val data = Map("documentaryUnit" -> List("create"))
        val perms = await(PermissionDAO(userProfile).get(user))
        perms.right.get.get(EntityType.DocumentaryUnit, PermissionType.Create) must beNone
        val permset = await(PermissionDAO(userProfile).set(user, data))
        permset must beRight
        permset.right.get.get(EntityType.DocumentaryUnit, PermissionType.Create) must beSome
      }
    }
    
  }

  step {
    runner.stop
  }
}