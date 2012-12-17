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
import defines.{EntityType,ContentType,PermissionType}
import models.UserProfileRepr
import models.DocumentaryUnitRepr
import models.DocumentaryUnitRepr
import models.DocumentaryUnitRepr

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class DAOSpec extends Specification with BeforeExample {
  sequential

  val testPort = 7575
  val config = Map("neo4j.server.port" -> testPort)
  val userProfile = UserProfileRepr(Entity.fromString("mike", EntityType.UserProfile))
  val entityType = EntityType.UserProfile

  val runner: ServerRunner = new ServerRunner(classOf[DAOSpec].getName, testPort)
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
        await(EntityDAO(entityType, Some(userProfile)).get(userProfile.identifier)) must beRight
      }
    }

    "create an item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> entityType.toString, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        await(EntityDAO(entityType, Some(userProfile)).create(data)) must beRight
      }
    }

    "create an item in (agent) context" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> EntityType.DocumentaryUnit.toString, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        val r = await(EntityDAO(EntityType.Agent, Some(userProfile)).createInContext(EntityType.DocumentaryUnit, "r1", data))
        r must beRight
        DocumentaryUnitRepr(r.right.get).holder must beSome
        DocumentaryUnitRepr(r.right.get).holder.get.identifier must equalTo("r1")
      }
    }

    "create an item in (doc) context" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> EntityType.DocumentaryUnit.toString, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        val r = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).createInContext(EntityType.DocumentaryUnit, "c1", data))
        r must beRight
        DocumentaryUnitRepr(r.right.get).parent must beSome
        DocumentaryUnitRepr(r.right.get).parent.get.identifier must equalTo("c1")
      }
    }
        
    "update an item by id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> entityType.toString, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        val entity = await(EntityDAO(entityType, Some(userProfile)).create(data)).right.get
        val udata = data + ("id" -> entity.id)
        val res = await(EntityDAO(entityType, Some(userProfile)).update(entity.id, udata))
        res must beRight
      }
    }

    "error when creating without a type" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        val err = await(EntityDAO(entityType, Some(userProfile)).create(data))
        err must beLeft
        err.left.get mustEqual DeserializationError
      }
    }

    "error when creating an item with a non-unique id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> "userProfile", "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        await(EntityDAO(entityType, Some(userProfile)).create(data))
        val err = await(EntityDAO(entityType, Some(userProfile)).create(data))
        err must beLeft
        err.left.get mustEqual IntegrityError
      }
    }

    "error when fetching a non-existing item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val err = await(EntityDAO(entityType, Some(userProfile)).get("blibidyblob"))
        err must beLeft
        err.left.get mustEqual ItemNotFound

      }
    }

    "delete an item by id" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val data = Map("id" -> None, "type" -> entityType.toString, "data" -> Map("identifier" -> "foobar", "name" -> "Foobar"))
        val entity = await(EntityDAO(entityType, Some(userProfile)).create(data)).right.get
        await(EntityDAO(entityType, Some(userProfile)).delete(entity.id)) must beRight
      }
    }

    "list items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        await(EntityDAO(entityType, Some(userProfile)).list(0, 20)) must beRight
      }
    }
    
    "page items" in {
      running(FakeApplication(additionalConfiguration = config)) {
        await(EntityDAO(entityType, Some(userProfile)).page(1, 20)) must beRight
      }
    }    
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
  
  "VisibilityDAO" should {
    "set visibility correctly" in {
      running(FakeApplication(additionalConfiguration = config)) {
        
        // First, fetch an object and assert its accessibility
        val c1a = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
        DocumentaryUnitRepr(c1a).accessors.map(_.identifier) must haveTheSameElementsAs(List("admin", "mike"))
        
        val set = await(VisibilityDAO(userProfile).set(DocumentaryUnitRepr(c1a), List("mike", "reto", "admin")))
        set must beRight
        val c1b = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
        DocumentaryUnitRepr(c1b).accessors.map(_.identifier) must haveTheSameElementsAs(List("admin", "mike", "reto"))    
      }
    }
  }
  
/*  "CypherDAO" should {
    "get a JsValue for a graph item" in {
      running(FakeApplication(additionalConfiguration = config)) {
        val dao = rest.cypher.CypherDAO()
        
        //println(await(WS.url("http://localhost:7575/db/data/").get).body)
        
        // FIXME: Cypher seems
        val res = await(dao.cypher("START n = node:userProfile('identifier:*') RETURN n.identifier, n.name"))
        res.right.map { r =>
          println(r)
        }
        res.left.map { err =>
          println(err + ": " + err.message + " - " + err.stacktrace)
        }
        res must beRight
        true must beTrue
      }
    }
  }
*/  
  step {
    runner.stop
  }
}
