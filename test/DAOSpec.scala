package test

import play.api.test._
import play.api.test.Helpers._
import rest._
import play.api.libs.concurrent.Execution.Implicits._
import models.{Repository, DocumentaryUnit, UserProfile, DocumentaryUnitF, UserProfileF}
import defines.{EntityType, ContentTypes, PermissionType}
import utils.ListParams

/**
 * Spec for testing individual data access components work as expected.
 */
class DAOSpec extends helpers.Neo4jRunnerSpec(classOf[DAOSpec]) {
  val userProfile = UserProfile(UserProfileF(id = Some("mike"), identifier = "mike", name = "Mike"))
  val entityType = EntityType.UserProfile

  //class FakeApp extends WithApplication(FakeApplication(additionalConfiguration = config, withGlobal = Some(getGlobal)))

  "EntityDAO" should {
    "get an item by id" in new FakeApp {
      await(EntityDAO[UserProfile](entityType, Some(userProfile)).get(userProfile.id)) must beRight
    }

    "create an item" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO[UserProfile](entityType, Some(userProfile)).create(user)) must beRight
    }

    "create an item in (agent) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO[Repository](EntityType.Repository, Some(userProfile))
          .createInContext[DocumentaryUnitF,DocumentaryUnit]("r1", ContentTypes.DocumentaryUnit, doc))
      r must beRight
      r.right.get.holder must beSome
      r.right.get.holder.get.id must equalTo("r1")
    }

    "create an item in (doc) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO[DocumentaryUnit](EntityType.DocumentaryUnit, Some(userProfile))
          .createInContext[DocumentaryUnitF,DocumentaryUnit]("c1", ContentTypes.DocumentaryUnit, doc))
      r must beRight
      r.right.get.parent must beSome
      r.right.get.parent.get.id must equalTo("c1")
    }

    "update an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(EntityDAO[UserProfile](entityType, Some(userProfile)).create(user)).right.get
      val udata = entity.model.copy(location = Some("London"))
      val res = await(EntityDAO[UserProfile](entityType, Some(userProfile)).update(entity.id, udata))
      res must beRight
      res.right.get.model.location must equalTo(Some("London"))
    }

    "error when creating an item with a non-unique id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO[UserProfile](entityType, Some(userProfile)).create(user))
      val err = await(EntityDAO[UserProfile](entityType, Some(userProfile)).create(user))
      err must beLeft
      err.left.get must beAnInstanceOf[ValidationError]
    }

    "error when fetching a non-existing item" in new FakeApp {
      val err = await(EntityDAO[UserProfile](entityType, Some(userProfile)).get("blibidyblob"))
      err must beLeft
      err.left.get must beAnInstanceOf[ItemNotFound]
    }

    "delete an item by id" in new FakeApp {
      val user = UserProfileF(id = Some("foobar"), identifier = "foo", name = "bar")
      val entity = await(EntityDAO[UserProfile](entityType, Some(userProfile)).create(user)).right.get
      await(EntityDAO(entityType, Some(userProfile)).delete(entity.id)) must beRight
    }

    "page items" in new FakeApp {
      val r = await(EntityDAO[UserProfile](entityType, Some(userProfile)).page(ListParams()))
      r must beRight
      r.right.get.items.length mustEqual 5
    }

    "list items" in new FakeApp {
      var r = await(EntityDAO[UserProfile](entityType, Some(userProfile)).list(ListParams()))
      r must beRight
      r.right.get.length mustEqual 5
    }

    "count items" in new FakeApp {
      var r = await(EntityDAO(entityType, Some(userProfile)).count(ListParams()))
      r must beRight
      r.right.get mustEqual 5L
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new FakeApp {
      val perms = await(PermissionDAO[UserProfile](Some(userProfile)).get)
      perms must beRight
      perms.right.get.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to set a user's permissions" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(
        ContentTypes.Repository.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString),
        ContentTypes.DocumentaryUnit.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString)
      )
      val perms = await(PermissionDAO(Some(userProfile)).get(user))
      perms.right.get.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      perms.right.get.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.right.get.get(ContentTypes.Repository, PermissionType.Create) must beNone
      perms.right.get.get(ContentTypes.Repository, PermissionType.Update) must beNone
      val permset = await(PermissionDAO(Some(userProfile)).set(user, data))
      permset must beRight
      val newperms = permset.right.get
      newperms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      newperms.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beSome
      newperms.get(ContentTypes.Repository, PermissionType.Create) must beSome
      newperms.get(ContentTypes.Repository, PermissionType.Update) must beSome
    }

    "be able to set a user's permissions within a scope" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(ContentTypes.DocumentaryUnit.toString -> List("create", "update", "delete"))
      val perms = await(PermissionDAO(Some(userProfile)).get(user))
      perms.right.get.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      perms.right.get.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.right.get.get(ContentTypes.Repository, PermissionType.Create) must beNone
      perms.right.get.get(ContentTypes.Repository, PermissionType.Update) must beNone
      await(PermissionDAO(Some(userProfile)).setScope(user, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val permset = await(PermissionDAO(Some(userProfile)).getItem(user, ContentTypes.DocumentaryUnit, "c1"))
      permset must beRight
      val newItemPerms = permset.right.get
      newItemPerms.get(PermissionType.Create) must beSome
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List("update", "delete")
      val perms = await(PermissionDAO(Some(userProfile)).getItem(user, ContentTypes.DocumentaryUnit, "c1"))
      perms.right.get.get(PermissionType.Update) must beNone
      perms.right.get.get(PermissionType.Delete) must beNone
      val permReq = await(PermissionDAO(Some(userProfile)).setItem(user, ContentTypes.DocumentaryUnit, "c1", data))
      permReq must beRight
      val newItemPerms = permReq.right.get
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new FakeApp {
      // First, fetch an object and assert its accessibility
      val c1a = await(EntityDAO[DocumentaryUnit](EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
      c1a.accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike"))

      val set = await(VisibilityDAO(Some(userProfile)).set[DocumentaryUnit](c1a.id, List("mike", "reto", "admin")))
      set must beRight
      val c1b = await(EntityDAO[DocumentaryUnit](EntityType.DocumentaryUnit, Some(userProfile)).get("c1")).right.get
      c1b.accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike", "reto"))
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
    runner.stop()
  }
}
