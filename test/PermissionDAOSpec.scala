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
import defines._
import defines.PermissionType
import models.UserProfileRepr
import models.base.Accessor

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
  val userProfile = UserProfileRepr(Entity.fromString("mike", EntityType.UserProfile)
      .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))
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
        val perms = await(PermissionDAO[UserProfileRepr](userProfile).get)
        perms must beRight
        perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
      }
    }
    
    "be able to set a user's permissions" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val user = UserProfileRepr(Entity.fromString("reto", EntityType.UserProfile))
        val data = Map("agent" -> List("create", "update", "delete"), "documentaryUnit" -> List("create", "update","delete"))
        val perms = await(PermissionDAO(userProfile).get(user))
        perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Create) must beNone
        perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Update) must beNone
        perms.right.get.get(ContentType.Agent, PermissionType.Create) must beNone
        perms.right.get.get(ContentType.Agent, PermissionType.Update) must beNone
        val permset = await(PermissionDAO(userProfile).set(user, data))
        permset must beRight
        val newperms = permset.right.get
        newperms.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
        newperms.get(ContentType.DocumentaryUnit, PermissionType.Update) must beSome
        newperms.get(ContentType.Agent, PermissionType.Create) must beSome
        newperms.get(ContentType.Agent, PermissionType.Update) must beSome
      }
    }
    
  }

  step {
    runner.stop
  }
}