package test

import play.api.test._
import play.api.test.Helpers._
import rest._
import play.api.libs.concurrent.Execution.Implicits._
import models.Entity
import defines.{EntityType, ContentType, PermissionType}
import models.UserProfile
import models.{DocumentaryUnit, DocumentaryUnitF, UserProfileF}
import rest.RestPageParams

/**
 * Spec for testing individual data access components work as expected.
 */
class DAOSpec extends helpers.Neo4jRunnerSpec(classOf[DAOSpec]) {
  val userProfile = UserProfile(Entity.fromString("mike", EntityType.UserProfile))
  val entityType = EntityType.UserProfile

  class FakeApp extends WithApplication(FakeApplication(additionalConfiguration = config, withGlobal = Some(FakeGlobal)))

  "EntityDAO" should {
    "get an item by id" in new FakeApp {
      await(EntityDAO(entityType, Some(userProfile)).get(userProfile.id)) must beRight
    }

    "create an item" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO(entityType, Some(userProfile)).create(user)) must beRight
    }

    "create an item in (agent) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO(EntityType.Repository, Some(userProfile)).createInContext("r1", ContentType.DocumentaryUnit, doc))
      r must beRight
      DocumentaryUnit(r.right.get).holder must beSome
      DocumentaryUnit(r.right.get).holder.get.id must equalTo("r1")
    }

    "create an item in (doc) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).createInContext("c1", ContentType.DocumentaryUnit, doc))
      r must beRight
      DocumentaryUnit(r.right.get).parent must beSome
      DocumentaryUnit(r.right.get).parent.get.id must equalTo("c1")
    }

    "update an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(EntityDAO(entityType, Some(userProfile)).create(user)).right.get
      val udata = UserProfile(entity).formable.copy(location = Some("London"))
      val res = await(EntityDAO(entityType, Some(userProfile)).update(entity.id, udata))
      res must beRight
    }

    "error when creating an item with a non-unique id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO(entityType, Some(userProfile)).create(user))
      val err = await(EntityDAO(entityType, Some(userProfile)).create(user))
      err must beLeft
      err.left.get must beAnInstanceOf[ValidationError]
    }

    "error when fetching a non-existing item" in new FakeApp {
      val err = await(EntityDAO(entityType, Some(userProfile)).get("blibidyblob"))
      err must beLeft
      err.left.get must beAnInstanceOf[ItemNotFound]
    }

    "delete an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(EntityDAO(entityType, Some(userProfile)).create(user)).right.get
      await(EntityDAO(entityType, Some(userProfile)).delete(entity.id)) must beRight
    }

    "page items" in new FakeApp {
      val r = await(EntityDAO(entityType, Some(userProfile)).page(RestPageParams()))
      r must beRight
      r.right.get.items.length mustEqual 5
    }

    "list items" in new FakeApp {
      var r = await(EntityDAO(entityType, Some(userProfile)).list(RestPageParams()))
      r must beRight
      r.right.get.length mustEqual 5
    }

    "count items" in new FakeApp {
      var r = await(EntityDAO(entityType, Some(userProfile)).count(RestPageParams()))
      r must beRight
      r.right.get mustEqual 5L
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new FakeApp {
      val perms = await(PermissionDAO[UserProfile](Some(userProfile)).get)
      perms must beRight
      perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to set a user's permissions" in new FakeApp {
      val user = UserProfile(Entity.fromString("reto", EntityType.UserProfile))
      val data = Map(
        ContentType.Repository.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString),
        ContentType.DocumentaryUnit.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString)
      )
      val perms = await(PermissionDAO(Some(userProfile)).get(user))
      perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Create) must beNone
      perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Update) must beNone
      perms.right.get.get(ContentType.Repository, PermissionType.Create) must beNone
      perms.right.get.get(ContentType.Repository, PermissionType.Update) must beNone
      val permset = await(PermissionDAO(Some(userProfile)).set(user, data))
      permset must beRight
      val newperms = permset.right.get
      newperms.get(ContentType.DocumentaryUnit, PermissionType.Create) must beSome
      newperms.get(ContentType.DocumentaryUnit, PermissionType.Update) must beSome
      newperms.get(ContentType.Repository, PermissionType.Create) must beSome
      newperms.get(ContentType.Repository, PermissionType.Update) must beSome
    }

    "be able to set a user's permissions within a scope" in new FakeApp {
      val user = UserProfile(Entity.fromString("reto", EntityType.UserProfile))
      val data = Map(ContentType.DocumentaryUnit.toString -> List("create", "update", "delete"))
      val perms = await(PermissionDAO(Some(userProfile)).get(user))
      perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Create) must beNone
      perms.right.get.get(ContentType.DocumentaryUnit, PermissionType.Update) must beNone
      perms.right.get.get(ContentType.Repository, PermissionType.Create) must beNone
      perms.right.get.get(ContentType.Repository, PermissionType.Update) must beNone
      await(PermissionDAO(Some(userProfile)).setScope(user, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val permset = await(PermissionDAO(Some(userProfile)).getItem(user, ContentType.DocumentaryUnit, "c1"))
      permset must beRight
      val newItemPerms = permset.right.get
      newItemPerms.get(PermissionType.Create) must beSome
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new FakeApp {
      val user = UserProfile(Entity.fromString("reto", EntityType.UserProfile))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List("update", "delete")
      val perms = await(PermissionDAO(Some(userProfile)).getItem(user, ContentType.DocumentaryUnit, "c1"))
      perms.right.get.get(PermissionType.Update) must beNone
      perms.right.get.get(PermissionType.Delete) must beNone
      val permReq = await(PermissionDAO(Some(userProfile)).setItem(user, ContentType.DocumentaryUnit, "c1", data))
      permReq must beRight
      val newItemPerms = permReq.right.get
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new FakeApp {
      // First, fetch an object and assert its accessibility
      val c1a = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
      DocumentaryUnit(c1a).accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike"))

      val set = await(VisibilityDAO(Some(userProfile)).set(c1a.id, List("mike", "reto", "admin")))
      set must beRight
      val c1b = await(EntityDAO(EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
      DocumentaryUnit(c1b).accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike", "reto"))
    }
  }

  "CypherDAO" should {
    "get a JsValue for a graph item" in new FakeApp {
      val dao = rest.cypher.CypherDAO(Some(userProfile))
      // FIXME: Cypher seems
      val res = await(dao.cypher("START n = node:entities('__ID__:admin') RETURN n.identifier, n.name"))
      res must beRight
      // It should return one list value in the data section
      val list = (res.right.get \ "data").as[List[List[String]]]
      list(0)(0) mustEqual ("admin")
    }
  }

  step {
    runner.stop
  }
}
