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
import models.{ EntityDAO, ValidationError }
import play.api.libs.concurrent.execution.defaultContext
import models.{ Entity, EntityTypes }
import models.UserProfile
import play.api.libs.json.JsString
import org.specs2.specification.BeforeExample
import models.IntegrityError
import models.DeserializationError
import models.ItemNotFound

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class EntityDAOSpec extends Specification with BeforeExample {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)
  val userProfile = Some(UserProfile(Entity(21, Map("identifier" -> JsString("mike")))))
  val entityType = EntityTypes.UserProfile

  val runner: ServerRunner = new ServerRunner(classOf[EntityDAOSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(
      classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
  runner.start

  def before = {
    runner.tearDown
    runner.setUp
  }

  "EntityDAO" should {
    "get an item by id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        await(EntityDAO(entityType, userProfile).get(21)) must beRight
      }
    }

    "create an item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("isA" -> entityType.toString, "identifier" -> "foobar", "name" -> "Foobar")
        await(EntityDAO(entityType, userProfile).create(data)) must beRight
      }
    }

    "update an item by id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("isA" -> entityType.toString, "identifier" -> "foobar", "name" -> "Foobar")
        val entity = await(EntityDAO(entityType, userProfile).create(data)).right.get
        await(EntityDAO(entityType, userProfile).update(entity.id, data)) must beRight
      }
    }

    "error when creating without an isA" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("identifier" -> "foobar", "name" -> "Foobar")
        val err = await(EntityDAO(entityType, userProfile).create(data))
        err must beLeft
        err.left.get mustEqual DeserializationError
      }
    }

    "error when creating an item with a non-unique id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("identifier" -> "foobar", "isA" -> "userProfile", "name" -> "Foobar")
        await(EntityDAO(entityType, userProfile).create(data))
        val err = await(EntityDAO(entityType, userProfile).create(data))
        err must beLeft
        err.left.get mustEqual IntegrityError
      }
    }

    "error when fetching a non-existing item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val err = await(EntityDAO(entityType, userProfile).get("blibidyblob"))
        err must beLeft
        err.left.get mustEqual ItemNotFound

      }
    }

    "delete an item by id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("isA" -> entityType.toString, "identifier" -> "foobar", "name" -> "Foobar")
        val entity = await(EntityDAO(entityType, userProfile).create(data)).right.get
        await(EntityDAO(entityType, userProfile).delete(entity.id)) must beRight
      }
    }

    "list items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        await(EntityDAO(entityType, userProfile).list) must beRight
      }
    }
  }

  step {
    runner.stop
  }
}