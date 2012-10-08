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

import models.{EntityDAO,ValidationError}

import play.api.libs.concurrent.execution.defaultContext

import models.Entity

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class EntityDAOSpec extends Specification {
  sequential

  // Load the test server using a different config...
  val config = ConfigFactory.load("test.conf")
  val port = config.getInt("neo4j.server.port")
  val endpoint = config.getString("neo4j.server.endpoint")

  val runner: ServerRunner = new ServerRunner(classOf[EntityDAOSpec].getName, port)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(
      classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/" + endpoint));
  runner.start();

  "EntityDAO" should {
    "get an item by id" in {
      running(FakeApplication()) {
        await(EntityDAO("userProfile").get(21)) must beRight
      }
    }

    "create an item" in {
      running(FakeApplication()) {
        val data = Map("isA" -> "userProfile", "identifier" -> "foobar", "name" -> "Foobar")
        await(EntityDAO("userProfile").create(data)) must beRight
      }
    }

    "update an item by id" in {
      running(FakeApplication()) {
        val data = Map("isA" -> "userProfile", "identifier" -> "foobar", "name" -> "Foobar")
        val entity = await(EntityDAO("userProfile").create(data)).right.get
        await(EntityDAO("userProfile").update(entity.id, data)) must beRight
      }
    }

    "error when creating without an isA" in {
      running(FakeApplication()) {
        val data = Map("identifier" -> "foobar", "name" -> "Foobar")
        val err = await(EntityDAO("userProfile").create(data))
        err must beLeft
        err.left.get mustEqual ValidationError
      }
    }

    "delete an item by id" in {
      running(FakeApplication()) {
        val data = Map("isA" -> "userProfile", "identifier" -> "foobar", "name" -> "Foobar")
        val entity = await(EntityDAO("userProfile").create(data)).right.get
        await(EntityDAO("userProfile").delete(entity.id)) must beRight
      }
    }

    "list items" in {
      running(FakeApplication()) {
        await(EntityDAO("userProfile").list) must beRight
      }
    }
  }

  step {
    runner.stop
  }
}