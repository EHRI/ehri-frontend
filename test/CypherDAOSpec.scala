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
import rest.cypher.CypherDAO
import play.api.libs.ws.WS

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class CypherDAOSpec extends Specification with BeforeExample {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)
  val userProfile = UserProfileRepr(Entity.fromString("mike", EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))
  val entityType = EntityType.UserProfile

  val runner: ServerRunner = new ServerRunner(classOf[CypherDAOSpec].getName, testPort)
  runner.getConfigurator
    .getThirdpartyJaxRsClasses()
    .add(new ThirdPartyJaxRsPackage(
      classOf[EhriNeo4jFramedResource[_]].getPackage.getName, "/ehri"));
  runner.start

  def before = {
    runner.tearDown
    runner.setUp
  }

  "GremlinDAO" should {
    "get a JsValue for a graph item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val dao = CypherDAO()
        
        //println(await(WS.url("http://localhost:7575/db/data/").get).body)
        
        val res = await(dao.cypher("START n = node(1) RETURN n"))
        res.left.map { err =>
          println(err + " - " + err.stacktrace)
          
        }
        res must beRight
      }
    }
  }

  step {
    runner.stop
  }
}