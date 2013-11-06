package test

import play.api.test._
import play.api.test.Helpers._
import rest._
import play.api.libs.concurrent.Execution.Implicits._
import models.{Repository, DocumentaryUnit, UserProfile, DocumentaryUnitF, UserProfileF}
import defines.{EntityType, ContentTypes, PermissionType}
import utils.{ListParams, PageParams}

/**
 * Spec for testing individual data access components work as expected.
 */
class DAOSpec extends helpers.Neo4jRunnerSpec(classOf[DAOSpec]) {
  val userProfile = UserProfile(UserProfileF(id = Some("mike"), identifier = "mike", name = "Mike"))
  val entityType = EntityType.UserProfile
  implicit val apiUser: ApiUser = ApiUser(Some(userProfile.id))

  //class FakeApp extends WithApplication(FakeApplication(additionalConfiguration = config, withGlobal = Some(getGlobal)))

  "EntityDAO" should {
    "get an item by id" in new FakeApp {
      await(EntityDAO(entityType).get[UserProfile](userProfile.id))
    }

    "create an item" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO(entityType).create[UserProfile,UserProfileF](user))
    }

    "create an item in (agent) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO(EntityType.Repository)
          .createInContext[DocumentaryUnitF,DocumentaryUnit]("r1", ContentTypes.DocumentaryUnit, doc))
      r.holder must beSome
      r.holder.get.id must equalTo("r1")
      mockIndexer.eventBuffer.last must equalTo("nl-r1-foobar")
    }

    "create an item in (doc) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(EntityDAO(EntityType.DocumentaryUnit)
          .createInContext[DocumentaryUnitF,DocumentaryUnit]("c1", ContentTypes.DocumentaryUnit, doc))
      r.parent must beSome
      r.parent.get.id must equalTo("c1")
    }

    "update an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(EntityDAO(entityType).create[UserProfile,UserProfileF](user))
      val udata = entity.model.copy(location = Some("London"))
      val res = await(EntityDAO(entityType).update[UserProfile,UserProfileF](entity.id, udata))
      res.model.location must equalTo(Some("London"))
    }

    "error when creating an item with a non-unique id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(EntityDAO(entityType).create[UserProfile,UserProfileF](user))
      try {
        await(EntityDAO(entityType).create[UserProfile,UserProfileF](user))
        failure("Expected a validation error!")
      } catch {
        case e: ValidationError =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "error when fetching a non-existing item" in new FakeApp {
      try {
        await(EntityDAO(entityType).get[UserProfile]("blibidyblob"))
        failure("Expected Item not found!")
      } catch {
        case e: ItemNotFound =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "delete an item by id" in new FakeApp {
      val user = UserProfileF(id = Some("foobar"), identifier = "foo", name = "bar")
      val entity = await(EntityDAO(entityType).create[UserProfile,UserProfileF](user))
      await(EntityDAO(entityType).delete(entity.id))
    }

    "page items" in new FakeApp {
      val r = await(EntityDAO(entityType).page[UserProfile](PageParams()))
      r.items.length mustEqual 5
    }

    "list items" in new FakeApp {
      var r = await(EntityDAO(entityType).list[UserProfile](ListParams()))
      r.length mustEqual 5
    }

    "count items" in new FakeApp {
      var r = await(EntityDAO(entityType).count(PageParams()))
      r mustEqual 5L
    }

    "emit appropriate signals" in new FakeApp {
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new FakeApp {
      val perms = await(PermissionDAO().get(userProfile))
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to set a user's permissions" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(
        ContentTypes.Repository.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString),
        ContentTypes.DocumentaryUnit.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString)
      )
      val perms = await(PermissionDAO().get(user))
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.get(ContentTypes.Repository, PermissionType.Create) must beNone
      perms.get(ContentTypes.Repository, PermissionType.Update) must beNone
      val newperms = await(PermissionDAO().set(user, data))
      newperms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      newperms.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beSome
      newperms.get(ContentTypes.Repository, PermissionType.Create) must beSome
      newperms.get(ContentTypes.Repository, PermissionType.Update) must beSome
    }

    "be able to set a user's permissions within a scope" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(ContentTypes.DocumentaryUnit.toString -> List("create", "update", "delete"))
      val perms = await(PermissionDAO().get(user))
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      perms.get(ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.get(ContentTypes.Repository, PermissionType.Create) must beNone
      perms.get(ContentTypes.Repository, PermissionType.Update) must beNone
      await(PermissionDAO().setScope(user, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val newItemPerms = await(PermissionDAO().getItem(user, ContentTypes.DocumentaryUnit, "c1"))
      newItemPerms.get(PermissionType.Create) must beSome
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List("update", "delete")
      val perms = await(PermissionDAO().getItem(user, ContentTypes.DocumentaryUnit, "c1"))
      perms.get(PermissionType.Update) must beNone
      perms.get(PermissionType.Delete) must beNone
      val newItemPerms = await(PermissionDAO().setItem(user, ContentTypes.DocumentaryUnit, "c1", data))
      newItemPerms.get(PermissionType.Update) must beSome
      newItemPerms.get(PermissionType.Delete) must beSome
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new FakeApp {
      // First, fetch an object and assert its accessibility
      val c1a = await(EntityDAO(EntityType.DocumentaryUnit).get[DocumentaryUnit]("c1"))
      c1a.accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike"))

      val set = await(VisibilityDAO().set[DocumentaryUnit](c1a.id, List("mike", "reto", "admin")))
      val c1b = await(EntityDAO(EntityType.DocumentaryUnit).get[DocumentaryUnit]("c1"))
      c1b.accessors.map(_.id) must haveTheSameElementsAs(List("admin", "mike", "reto"))
    }
  }

  "CypherDAO" should {
    "get a JsValue for a graph item" in new FakeApp {
      val dao = rest.cypher.CypherDAO(Some(userProfile))
      // FIXME: Cypher seems
      val res = await(dao.cypher("START n = node:entities('__ID__:admin') RETURN n.identifier, n.name"))
      // It should return one list value in the data section
      val list = (res \ "data").as[List[List[String]]]
      list(0)(0) mustEqual ("admin")
    }
  }
}
