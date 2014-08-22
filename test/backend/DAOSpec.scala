package backend

import models._
import defines.{EntityType, ContentTypes, PermissionType}
import utils.PageParams
import backend.rest.{CypherIdGenerator, ItemNotFound, ValidationError}
import backend.rest.cypher.CypherDAO
import play.api.libs.json.{JsString, Json}

/**
 * Spec for testing individual data access components work as expected.
 */
class DAOSpec extends helpers.Neo4jRunnerSpec(classOf[DAOSpec]) {
  val userProfile = UserProfile(UserProfileF(id = Some("mike"), identifier = "mike", name = "Mike"))
  val entityType = EntityType.UserProfile
  implicit val apiUser: ApiUser = ApiUser(Some(userProfile.id))

  "EntityDAO" should {
    "not cache invalid responses (as per bug #324)" in new FakeApp {
      // Previously this would error the second time because the bad
      // response would be cached and error on JSON deserialization.
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
    }

    "get an item by id" in new FakeApp {
      await(testBackend.get[UserProfile](userProfile.id))
    }

    "create an item" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user))
    }

    "create an item in (agent) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[Repository,DocumentaryUnitF,DocumentaryUnit]("r1", ContentTypes.DocumentaryUnit, doc))
      r.holder must beSome
      r.holder.get.id must equalTo("r1")
      // This triggers an update event for the parent and a create
      // event for the new child
      val events = mockIndexer.eventBuffer.takeRight(2)
      events.headOption must beSome.which(_ must equalTo("r1"))
      events.lastOption must beSome.which(_ must equalTo("nl-r1-foobar"))
    }

    "create an item in (doc) context" in new FakeApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[DocumentaryUnit,DocumentaryUnitF,DocumentaryUnit]("c1", ContentTypes.DocumentaryUnit, doc))
      r.parent must beSome
      r.parent.get.id must equalTo("c1")
    }

    "create an item with additional params" in new FakeApp {
      val doc = VirtualUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
        .createInContext[VirtualUnit,VirtualUnitF,VirtualUnit]("vc1", ContentTypes.VirtualUnit,
            doc, params = Map("id" -> Seq("c1"))))
      r.includedUnits.headOption must beSome.which { desc =>
        desc.id must equalTo("c1")
      }
    }

    "update an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val udata = entity.model.copy(location = Some("London"))
      val res = await(testBackend.update[UserProfile,UserProfileF](entity.id, udata))
      res.model.location must equalTo(Some("London"))
    }

    "patch an item by id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val testVal = "http://example.com/"
      val patchData = Json.obj(UserProfileF.IMAGE_URL -> testVal)
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val res = await(testBackend.patch[UserProfile](entity.id, patchData))
      res.model.imageUrl must equalTo(Some(testVal))
    }

    "error when creating an item with a non-unique id" in new FakeApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user))
      try {
        await(testBackend.create[UserProfile,UserProfileF](user))
        failure("Expected a validation error!")
      } catch {
        case e: ValidationError =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "error when fetching a non-existing item" in new FakeApp {
      try {
        await(testBackend.get[UserProfile]("blibidyblob"))
        failure("Expected Item not found!")
      } catch {
        case e: ItemNotFound =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "delete an item by id" in new FakeApp {
      val user = UserProfileF(id = Some("foobar"), identifier = "foo", name = "bar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      await(testBackend.delete[UserProfile](entity.id))
    }

    "page items" in new FakeApp {
      val r = await(testBackend.list[UserProfile]())
      r.items.length mustEqual 5
      r.page mustEqual 1
      r.count mustEqual backend.rest.Constants.DEFAULT_LIST_LIMIT
      r.total mustEqual 5
    }

    "count items" in new FakeApp {
      var r = await(testBackend.count[UserProfile](PageParams()))
      r mustEqual 5L
    }

    "emit appropriate signals" in new FakeApp {
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new FakeApp {
      val perms = await(testBackend.getGlobalPermissions(userProfile.id))
      perms.get(userProfile, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to set a user's permissions" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(
        ContentTypes.Repository.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString),
        ContentTypes.DocumentaryUnit.toString -> List(PermissionType.Create.toString, PermissionType.Update.toString, PermissionType.Delete.toString)
      )
      val perms = await(testBackend.getGlobalPermissions(user.id))
      perms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      perms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.get(user, ContentTypes.Repository, PermissionType.Create) must beNone
      perms.get(user, ContentTypes.Repository, PermissionType.Update) must beNone
      val newperms = await(testBackend.setGlobalPermissions(user.id, data))
      newperms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      newperms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Update) must beSome
      newperms.get(user, ContentTypes.Repository, PermissionType.Create) must beSome
      newperms.get(user, ContentTypes.Repository, PermissionType.Update) must beSome
    }

    "be able to set a user's permissions within a scope" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(ContentTypes.DocumentaryUnit.toString -> List("update", "delete"))
      val perms = await(testBackend.getScopePermissions(user.id, "r1"))
      perms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      perms.get(user, ContentTypes.DocumentaryUnit, PermissionType.Delete) must beNone
      perms.get(user, ContentTypes.Repository, PermissionType.Create) must beNone
      perms.get(user, ContentTypes.Repository, PermissionType.Update) must beNone
      await(testBackend.setScopePermissions(user.id, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val newItemPerms = await(testBackend.getItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      newItemPerms.get(user, PermissionType.Update) must beSome
      newItemPerms.get(user, PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new FakeApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List("update", "delete")
      val perms = await(testBackend.getItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      perms.get(user, PermissionType.Update) must beNone
      perms.get(user, PermissionType.Delete) must beNone
      val newItemPerms = await(testBackend.setItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1", data))
      newItemPerms.get(user, PermissionType.Update) must beSome
      newItemPerms.get(user, PermissionType.Delete) must beSome
    }

    "be able to list permissions" in new FakeApp {
      val page = await(testBackend.listScopePermissionGrants("r1", PageParams.empty))
      page.items must not(beEmpty)
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new FakeApp {
      // First, fetch an object and assert its accessibility
      val c1a = await(testBackend.get[DocumentaryUnit]("c1"))
      c1a.accessors.map(_.id) must containAllOf(List("admin", "mike"))

      val set = await(testBackend.setVisibility[DocumentaryUnit](c1a.id, List("mike", "reto", "admin")))
      val c1b = await(testBackend.get[DocumentaryUnit]("c1"))
      c1b.accessors.map(_.id) must containAllOf(List("admin", "mike", "reto"))
    }

    "promote and demote items" in new FakeApp {
      val ann1: Annotation = await(testBackend.get[Annotation]("ann1"))
      ann1.promotors must beEmpty
      await(testBackend.promote("ann1")) must beTrue
      val ann1_2: Annotation = await(testBackend.get[Annotation]("ann1"))
      (ann1_2.promotors must not).beEmpty
      private val pid: String = ann1_2.promotors.head.id
      pid must equalTo(apiUser.id.get)

      await(testBackend.demote("ann1")) must beTrue
      val ann1_3: Annotation = await(testBackend.get[Annotation]("ann1"))
      ann1_3.promotors must beEmpty
    }
  }

  "SocialDAO" should {
    "allow following and unfollowing" in new FakeApp {
      await(testBackend.isFollowing(userProfile.id, "reto")) must beFalse
      await(testBackend.follow(userProfile.id, "reto"))
      await(testBackend.isFollowing(userProfile.id, "reto")) must beTrue
      val following = await(testBackend.following(userProfile.id))
      following.exists(_.id == "reto") must beTrue
      val followingPage = await(testBackend.following(userProfile.id))
      followingPage.total must equalTo(1)
      await(testBackend.unfollow(userProfile.id, "reto"))
      await(testBackend.isFollowing(userProfile.id, "reto")) must beFalse
    }

    "allow watching and unwatching" in new FakeApp {
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
      await(testBackend.watch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beTrue
      val watching = await(testBackend.watching(userProfile.id))
      watching.exists(_.id == "c1") must beTrue
      val watchingPage = await(testBackend.watching(userProfile.id))
      watchingPage.total must equalTo(1)
      await(testBackend.unwatch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
    }

    "allow blocking and unblocking" in new FakeApp {
      await(testBackend.isBlocking(userProfile.id, "reto")) must beFalse
      await(testBackend.block(userProfile.id, "reto"))
      await(testBackend.isBlocking(userProfile.id, "reto")) must beTrue
      val blocking = await(testBackend.blocked(userProfile.id))
      blocking.exists(_.id == "reto") must beTrue
      val blockingPage = await(testBackend.blocked(userProfile.id))
      blockingPage.total must equalTo(1)
      await(testBackend.unblock(userProfile.id, "reto"))
      await(testBackend.isBlocking(userProfile.id, "reto")) must beFalse
    }

    "handle virtual collections" in new FakeApp {
      val data = VirtualUnitF(
        identifier = "vc-test",
        descriptions = List(
          DocumentaryUnitDescriptionF(
            languageCode = "eng",
            identity = IsadGIdentity(name = "VC Test")
          )
        )
      )
      // Create a new VC
      val vc = await(testBackend.create[VirtualUnit,VirtualUnitF](data))
      vc.id must equalTo("vc-test")
      vc.author must beSome.which { author =>
        author.id must equalTo(userProfile.id)
      }

      // Ensure we can load VCs for the user
      val vcs = await(testBackend.userBookmarks(userProfile.id))
      vcs.size must equalTo(1)
      vcs.headOption must beSome.which { ovc =>
        ovc.id must equalTo(vc.id)
      }

      vc.includedUnits.size must equalTo(0)

      // Add an included unit to the VC (a bookmark)
      await(testBackend.addBookmark(vc.id, "c4"))
      await(testBackend.userBookmarks(userProfile.id)).headOption must beSome.which { ovc =>
        ovc.includedUnits.size must equalTo(1)
        ovc.includedUnits.headOption must beSome.which { iu =>
          iu.id must equalTo("c4")
        }
      }

      // Delete the included unit
      await(testBackend.deleteBookmarks(vc.id, Seq("c4")))
      await(testBackend.userBookmarks(userProfile.id)).headOption must beSome.which { ovc =>
        ovc.includedUnits.size must equalTo(0)
      }
    }
  }

  "CypherDAO" should {
    "get a JsValue for a graph item" in new FakeApp {
      val dao = new CypherDAO
      val res = await(dao.cypher(
        """START n = node:entities(__ID__ = {id})
           RETURN n.identifier, n.name""",
          Map("id" -> JsString("admin"))))
      // It should return one list value in the data section
      val list = (res \ "data").as[List[List[String]]]
      list(0)(0) mustEqual "admin"
    }
  }

  "CypherIdGenerator" should {
    "get the right next ID for repositories" in new FakeApp {
      val idGen = new CypherIdGenerator("%06d")
      await(idGen.getNextNumericIdentifier(EntityType.Repository)) must equalTo("000005")
    }

    "get the right next ID for collections in scope" in new FakeApp {
      // There a 4 collections in the fixtures c1-c4
      val idGen = new CypherIdGenerator("c%01d")
      await(idGen.getNextChildNumericIdentifier("r1", EntityType.DocumentaryUnit)) must equalTo("c5")
    }
  }
}
