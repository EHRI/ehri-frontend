package services.data

import akka.stream.scaladsl.Source
import helpers.IntegrationTestRunner
import models._
import play.api.Configuration
import play.api.cache.SyncCacheApi
import play.api.libs.json.{JsNull, JsObject, JsString, Json}
import play.api.libs.ws.WSClient
import services.cypher.WsCypherService
import utils.SystemEventParams.Aggregation
import utils.{PageParams, RangePage, RangeParams, SystemEventParams}

import scala.concurrent.ExecutionContext


/**
 * Spec for testing individual data access components work as expected.
 */
class WsDataServiceSpec extends IntegrationTestRunner {
  sequential

  val userProfile = UserProfile(UserProfileF(id = Some("mike"), identifier = "mike", name = "Mike"))
  val entityType = EntityType.UserProfile
  implicit val apiUser: DataUser = AuthenticatedUser(userProfile.id)

  def testBackend(implicit app: play.api.Application, apiUser: DataUser, ec: ExecutionContext): DataService =
    app.injector.instanceOf[DataServiceBuilder].withContext(apiUser)


  private def ws(implicit app: play.api.Application) = app.injector.instanceOf[WSClient]
  private def config(implicit app: play.api.Application) = app.injector.instanceOf[Configuration]
  private def cache(implicit app: play.api.Application) = app.injector.instanceOf[SyncCacheApi]

  /**
   * A minimal object that has a resource type and can be read.
   */
  case class TestResource(id: String, data: JsObject) extends WithId
  object TestResource {
    implicit object Resource extends Resource[TestResource] {
      def entityType: EntityType.Value = EntityType.DocumentaryUnit
      import play.api.libs.functional.syntax._
      import play.api.libs.json._
      val _reads: Reads[TestResource] = (
        (__ \ Entity.ID).read[String] and
          (__ \ Entity.DATA).read[JsObject]
        )(TestResource.apply _)
    }
  }

  "RestBackend" should {
    "allow fetching single objects" in new ITestApp {
      val test: TestResource = await(testBackend.get[TestResource]("c1"))
      test.id must equalTo("c1")
    }

    "allow fetching multiple objects" in new ITestApp {
      val test: Seq[Option[TestResource]] = await(testBackend.fetch[TestResource](Seq("c1", "bad)")))
      test.size must_== 2
      test.head must beSome.which { i =>
        i.id must_== "c1"
      }
      test(1) must beNone
    }
  }

  "Generic entity operations" should {
    "not cache invalid responses (as per bug #324)" in new ITestApp {
      // Previously this would error the second time because the bad
      // response would be cached and error on JSON deserialization.
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
    }

    "not error when retrieving existing items at the wrong path (as per bug #500)" in new ITestApp {
      // Load the item (populating the cache)
      await(testBackend.get[DocumentaryUnit]("c1"))
      // Now try and fetch it under a different resource path...
      await(testBackend.get[UserProfile]("c1")) must throwA[ItemNotFound]
    }

    "get an item by id" in new ITestApp {
      val profile: UserProfile = await(testBackend.get[UserProfile](userProfile.id))
      profile.id must equalTo(userProfile.id)
    }

    "create an item" in new ITestApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user))
    }

    "use higher characters in log messages" in new ITestApp {
      val msg = "This is a 日本語メッセージ"
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user, logMsg = Some(msg)))
      val history = await(testBackend.history[SystemEvent]("foobar", RangeParams(limit = 1)))
      history.items.size must_== 1
      history.head.size must_== 1
      history.head.head.data.logMessage must beSome(msg)
    }

    "create an item in (agent) context" in new ITestApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[Repository,DocumentaryUnitF,DocumentaryUnit]("r1", doc))
      r.holder must beSome
      r.holder.get.id must equalTo("r1")
      // This triggers an update event for the parent and a create
      // event for the new child
      val events = indexEventBuffer.takeRight(2)
      events.headOption must beSome.which(_ must equalTo("r1"))
      events.lastOption must beSome.which(_ must equalTo("nl-r1-foobar"))
    }

    "create an item in (doc) context" in new ITestApp {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[DocumentaryUnit,DocumentaryUnitF,DocumentaryUnit]("c1", doc))
      r.parent must beSome
      r.parent.get.id must equalTo("c1")
    }

    "create an item with additional params" in new ITestApp {
      val doc = VirtualUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
        .createInContext[VirtualUnit,VirtualUnitF,VirtualUnit]("vc1",
            doc, params = Map("id" -> Seq("c1"))))
      r.includedUnits.headOption must beSome.which { desc =>
        desc.id must equalTo("c1")
      }
    }

    "error deleting children w/ children" in new ITestApp {
      await(testBackend.deleteChildren[DocumentaryUnit]("c1")) must throwA[HierarchyError]
    }

    "delete a doc and its child items" in new ITestApp {
      val items = Seq("c2", "c3")
      items.foreach { id =>
        await(testBackend.get[DocumentaryUnit](id))
        cache.get(s"item:$id") must beSome
      }
      val ids = await(testBackend.deleteChildren[DocumentaryUnit]("c1", all = true))
      ids must_== items
      ids.foreach(id => cache.get(s"item:$id") must beNone)
    }

    "give a 410 error fetching a deleted item" in new ITestApp {
      await(testBackend.delete[DocumentaryUnit]("c4"))
      await(testBackend.get[DocumentaryUnit]("c4")) must throwA[ItemNotFound].like {
        case e: ItemNotFound => e.since must beSome
      }
    }

    "update an item by id" in new ITestApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val udata = entity.data.copy(location = Some("London"))
      val res = await(testBackend.update[UserProfile,UserProfileF](entity.id, udata))
      res.data.location must beSome("London")
    }

    "delete an item by id" in new ITestApp {
      await(testBackend.delete[UserProfile]("reto"))
      await(testBackend.get[UserProfile]("reto")) must throwA[ItemNotFound]
    }

    "patch an item by id" in new ITestApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val testVal = "http://example.com/"
      val patchData = Json.obj(UserProfileF.IMAGE_URL -> testVal)
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val res = await(testBackend.patch[UserProfile](entity.id, patchData))
      res.data.imageUrl must beSome(testVal)
    }

    "patch an item with nulls to unset values" in new ITestApp {
      val testVal = Some("test")
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar", about = testVal)
      val patchData = Json.obj("about" -> JsNull)
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      entity.data.about must_== testVal
      val res = await(testBackend.patch[UserProfile](entity.id, patchData))
      res.data.about must beNone
    }

    "error when creating an item with a non-unique id" in new ITestApp {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user))
      try {
        await(testBackend.create[UserProfile,UserProfileF](user))
        failure("Expected a validation error!")
      } catch {
        case e: services.data.ValidationError =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "error when fetching a non-existing item" in new ITestApp {
      try {
        await(testBackend.get[UserProfile]("blibidyblob"))
        failure("Expected Item not found!")
      } catch {
        case e: ItemNotFound =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "error with context data when deserializing JSON incorrectly" in new ITestApp {
      try {
        // deliberate use the wrong readable here to generate a
        // deserialization error...
        import models.Accessor
        import models.json.JsPathExtensions
        import play.api.libs.functional.syntax._
        import play.api.libs.json._
        val badDeserializer: ContentType[UserProfile] = new ContentType[UserProfile] {
          val _reads: Reads[UserProfile] = (
            __.read[UserProfileF] and
            __.lazyReadSeqOrEmpty(Group.GroupResource._reads) and
            __.lazyReadSeqOrEmpty(Accessor.Converter._reads) and
            __.readHeadNullable[SystemEvent] and
            __.read[JsObject]
          )(UserProfile.quickApply _)
          val entityType: EntityType.Value = UserProfile.UserProfileResource.entityType
          val contentType: ContentTypes.Value = UserProfile.UserProfileResource.contentType
        }

        await(testBackend.get[UserProfile]("mike")(badDeserializer))
        failure("Expected BadJson error was not found!")
      } catch {
        case e: BadJson =>
          e.url must beSome.which { url =>
            url must endWith(s"/${UserProfile.UserProfileResource.entityType}/mike")
          }
        case _: Throwable => failure("Expected a json error!")
      }
    }

    "allow linking two items" in new ITestApp {
      val data = LinkF(id = None, linkType = LinkF.LinkType.Copy, description = Some("test"))
      val link = await(testBackend.linkItems[DocumentaryUnit, Link, LinkF](
        "c1", "r1", link = data, directional = true))
      link.source must beSome.which(s => s.id must_== "c1")
    }

    "delete an item by id" in new ITestApp {
      val user = UserProfileF(id = Some("foobar"), identifier = "foo", name = "bar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      await(testBackend.delete[UserProfile](entity.id))
    }

    "page items" in new ITestApp {
      val r = await(testBackend.list[UserProfile]())
      r.items.length mustEqual 5
      r.page mustEqual 1
      r.limit mustEqual services.data.Constants.DEFAULT_LIST_LIMIT
      r.total mustEqual 5
    }

    "stream items" in new ITestApp {
      val s = testBackend.stream[UserProfile]()
      await(s.runFold(0) { case (acc, _) => acc + 1}) must_== 5
    }

    "count items" in new ITestApp {
      var r = await(testBackend.count[UserProfile]())
      r mustEqual 5L
    }

    "count child items" in new ITestApp {
      var r = await(testBackend.countChildren[DocumentaryUnit]("c1"))
      r mustEqual 1L
    }

    "retrieve a user's info" in new ITestApp {
      val links = await(testBackend.userLinks[Link]("mike"))
      links.size must_== 3
      val notes = await(testBackend.userAnnotations[Annotation]("mike"))
      notes.size must_== 2
      val vus = await(testBackend.userBookmarks[VirtualUnit]("linda"))
      vus.size must_== 1
    }
  }

  "Batch operations" should {
    "delete items as a batch" in new ITestApp {
      val count = await(testBackend.batchDelete(Seq("c1", "c4"),
        scope = Some("r1"), version = true, commit = true, logMsg = "test"))
      count must_== 2
    }

    "update items via a JSON stream" in new ITestApp {
      val src = Source(List(Json.obj(
        "id" -> "r1",
        "type" -> "Repository",
        "data" -> Json.obj("longitude" -> 0.5, "latitude" -> 0.5)
      ), Json.obj(
        "id" -> "r2",
        "type" -> "Repository",
        "data" -> Json.obj("longitude" -> 0.5, "latitude" -> 0.5)
      )))

      val log = await(testBackend.batchUpdate(src, scope = None, version = true,
        commit = true, logMsg = "test"))
      log.updated must_== 2
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new ITestApp {
      val perms = await(testBackend.globalPermissions(userProfile.id))
      userProfile.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to add and revoke a user's global permissions" in new ITestApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val perms = await(testBackend.globalPermissions(user.id))
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Create) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Update) must beNone

      val dataAdd = Map(
        ContentTypes.Repository.toString -> List(
          PermissionType.Create.toString,
          PermissionType.Update.toString,
          PermissionType.Delete.toString
        ),
        ContentTypes.DocumentaryUnit.toString -> List(
          PermissionType.Create.toString,
          PermissionType.Update.toString,
          PermissionType.Delete.toString
        )
      )
      val newPermsAdd = await(testBackend.setGlobalPermissions(user.id, dataAdd))
      user.getPermission(newPermsAdd, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
      user.getPermission(newPermsAdd, ContentTypes.DocumentaryUnit, PermissionType.Update) must beSome
      user.getPermission(newPermsAdd, ContentTypes.Repository, PermissionType.Create) must beSome
      user.getPermission(newPermsAdd, ContentTypes.Repository, PermissionType.Update) must beSome

      val dataRem = Map(
        ContentTypes.Repository.toString -> List.empty,
        ContentTypes.DocumentaryUnit.toString -> List.empty
      )
      val newPermsRem = await(testBackend.setGlobalPermissions(user.id, dataRem))
      user.getPermission(newPermsRem, ContentTypes.DocumentaryUnit, PermissionType.Create) must beNone
      user.getPermission(newPermsRem, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      user.getPermission(newPermsRem, ContentTypes.DocumentaryUnit, PermissionType.Delete) must beNone
      user.getPermission(newPermsRem, ContentTypes.Repository, PermissionType.Create) must beNone
      user.getPermission(newPermsRem, ContentTypes.Repository, PermissionType.Update) must beNone
      user.getPermission(newPermsRem, ContentTypes.Repository, PermissionType.Delete) must beNone
    }

    "be able to set a user's permissions within a scope" in new ITestApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(ContentTypes.DocumentaryUnit.toString -> List(
        PermissionType.Update.toString,
        PermissionType.Delete.toString)
      )
      val perms = await(testBackend.scopePermissions(user.id, "r1"))
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Delete) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Create) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Update) must beNone
      await(testBackend.setScopePermissions(user.id, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val newItemPerms = await(testBackend.itemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      user.getPermission(newItemPerms, PermissionType.Update) must beSome
      user.getPermission(newItemPerms, PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new ITestApp {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List(PermissionType.Update.toString, PermissionType.Delete.toString)
      val perms = await(testBackend.itemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      user.getPermission(perms, PermissionType.Update) must beNone
      user.getPermission(perms, PermissionType.Delete) must beNone
      val newItemPerms = await(testBackend.setItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1", data))
      user.getPermission(newItemPerms, PermissionType.Update) must beSome
      user.getPermission(newItemPerms, PermissionType.Delete) must beSome
    }

    "be able to list permissions" in new ITestApp {
      val page = await(testBackend.scopePermissionGrants[PermissionGrant]("r1", PageParams.empty))
      page.items must not(beEmpty)
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new ITestApp {
      // First, fetch an object and assert its accessibility
      val c1a = await(testBackend.get[DocumentaryUnit]("c1"))
      c1a.accessors.map(_.id) must containAllOf(List("admin", "mike"))

      val set = await(testBackend.setVisibility[DocumentaryUnit](c1a.id, List("mike", "reto", "admin")))
      val c1b = await(testBackend.get[DocumentaryUnit]("c1"))
      c1b.accessors.map(_.id) must containAllOf(List("admin", "mike", "reto"))
    }

    "promote and demote items" in new ITestApp {
      val ann1: Annotation = await(testBackend.get[Annotation]("ann1"))
      ann1.promoters must beEmpty
      val promoted = await(testBackend.promote[Annotation]("ann1"))
      promoted.demoters must beEmpty
      promoted.promoters.headOption must beSome.which { promoter =>
        promoter.id must equalTo(userProfile.id)
      }

      val demoted = await(testBackend.demote[Annotation]("ann1"))
      demoted.promoters must beEmpty
      demoted.demoters.headOption must beSome.which { demoter =>
        demoter.id must equalTo(userProfile.id)
      }
    }
  }

  "Event operations" should {
    "handle paging correctly" in new ITestApp {
      // Modify an item 3 times and check the events work properly
      await(testBackend.patch[DocumentaryUnit]("c1", Json.obj("prop1" -> "foo")))
      await(testBackend.patch[DocumentaryUnit]("c1", Json.obj("prop2" -> "foo")))
      await(testBackend.patch[DocumentaryUnit]("c1", Json.obj("prop3" -> "foo")))

      // Fetching all the items (limit = -1), implies there are never more
      // items because we've got them all...
      // NB: Turning off event aggregation, because otherwise all events
      // will come in one batch (since they're by the same user)
      val eventParams = SystemEventParams.empty.copy(aggregation = Some(Aggregation.Off))
      val pageNoLimit: RangePage[Seq[SystemEvent]] =
        await(testBackend.history[SystemEvent]("c1", RangeParams.empty.withoutLimit, eventParams))
      pageNoLimit.size must equalTo(3) // 1 user
      pageNoLimit.head.size must equalTo(1)
      pageNoLimit.more must beFalse
      pageNoLimit.limit must equalTo(-1)

      val pageOne: RangePage[Seq[SystemEvent]] =
        await(testBackend.history[SystemEvent]("c1", RangeParams(limit = 2), eventParams))
      pageOne.size must equalTo(2)
      pageOne.more must beTrue
      pageOne.offset must equalTo(0)
      pageOne.limit must equalTo(2)

      val pageOneThree: RangePage[Seq[SystemEvent]] =
        await(testBackend.history[SystemEvent]("c1", RangeParams(offset = 2, limit = 1), eventParams))
      pageOneThree.size must equalTo(1)
      pageOneThree.more must beFalse
      pageOneThree.offset must equalTo(2)
      pageOneThree.limit must equalTo(1)
    }

    "get activity for a user" in new ITestApp {
      await(testBackend.patch[DocumentaryUnit]("c1", Json.obj("prop1" -> "foo")))
      val forUser: RangePage[Seq[SystemEvent]] =
        await(testBackend.userEvents[SystemEvent](userProfile.id, RangeParams.empty.withoutLimit))
      forUser.size must equalTo(1)

      val byUser: RangePage[Seq[SystemEvent]] =
        await(testBackend.userActions[SystemEvent](userProfile.id, RangeParams.empty.withoutLimit))
      byUser.size must equalTo(1)
    }
  }

  "Social operations" should {
    "allow following and unfollowing" in new ITestApp {
      await(testBackend.isFollowing(userProfile.id, "reto")) must beFalse
      await(testBackend.follow[UserProfile](userProfile.id, "reto"))
      await(testBackend.isFollowing(userProfile.id, "reto")) must beTrue
      val following = await(testBackend.following[UserProfile](userProfile.id))
      following.exists(_.id == "reto") must beTrue
      val followingPage = await(testBackend.following[UserProfile](userProfile.id))
      followingPage.total must equalTo(1)
      await(testBackend.unfollow[UserProfile](userProfile.id, "reto"))
      await(testBackend.isFollowing(userProfile.id, "reto")) must beFalse
    }

    "allow watching and unwatching" in new ITestApp {
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
      await(testBackend.watch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beTrue
      val watching = await(testBackend.watching[Model](userProfile.id))
      watching.exists(_.id == "c1") must beTrue
      val watchingPage = await(testBackend.watching[Model](userProfile.id))
      watchingPage.total must equalTo(1)
      await(testBackend.unwatch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
    }

    "allow blocking and unblocking" in new ITestApp {
      await(testBackend.isBlocking(userProfile.id, "reto")) must beFalse
      await(testBackend.block(userProfile.id, "reto"))
      await(testBackend.isBlocking(userProfile.id, "reto")) must beTrue
      val blocking = await(testBackend.blocked[UserProfile](userProfile.id))
      blocking.exists(_.id == "reto") must beTrue
      val blockingPage = await(testBackend.blocked[UserProfile](userProfile.id))
      blockingPage.total must equalTo(1)
      await(testBackend.unblock(userProfile.id, "reto"))
      await(testBackend.isBlocking(userProfile.id, "reto")) must beFalse
    }

    "handle virtual collections (and bookmarks)" in new ITestApp {
      val data = VirtualUnitF(
        identifier = "vctest",
        descriptions = List(
          DocumentaryUnitDescriptionF(
            languageCode = "eng",
            identity = IsadGIdentity(name = "VC Test")
          )
        )
      )
      // Create a new VC
      val vc = await(testBackend.create[VirtualUnit,VirtualUnitF](data))
      vc.id must equalTo("vctest")
      vc.author must beSome.which { author =>
        author.id must equalTo(userProfile.id)
      }

      // Ensure we can load VCs for the user
      val vcs = await(testBackend.userBookmarks[VirtualUnit](userProfile.id))
      vcs.size must equalTo(1)
      vcs.headOption must beSome.which { ovc =>
        ovc.id must equalTo(vc.id)
      }

      vc.includedUnits.size must equalTo(0)

      // Add an included unit to the VC (a bookmark)
      await(testBackend.addReferences[VirtualUnit](vc.id, Seq("c4")))
      await(testBackend.userBookmarks[VirtualUnit](userProfile.id)).headOption must beSome.which { ovc =>
        ovc.includedUnits.size must equalTo(1)
        ovc.includedUnits.headOption must beSome.which { iu =>
          iu.id must equalTo("c4")
        }
      }

      // Delete the included unit
      await(testBackend.deleteReferences[VirtualUnit](vc.id, Seq("c4")))
      await(testBackend.userBookmarks[VirtualUnit](userProfile.id)).headOption must beSome.which { ovc =>
        ovc.includedUnits.size must equalTo(0)
      }

      // Moving included units...
      val vc2 = await(testBackend.create[VirtualUnit,VirtualUnitF](
        data.copy(identifier = "vctest2")))
      await(testBackend.addReferences[VirtualUnit](vc.id, Seq("c1")))
      await(testBackend.get[VirtualUnit](vc.id))
        .includedUnits.map(_.id) must contain("c1")
      await(testBackend.addReferences[VirtualUnit](vc2.id, Seq("c2")))
      await(testBackend.get[VirtualUnit](vc2.id))
        .includedUnits.map(_.id) must contain("c2")
      await(testBackend.moveReferences[VirtualUnit](vc.id, vc2.id, Seq("c1")))
      await(testBackend.get[VirtualUnit](vc.id))
        .includedUnits.map(_.id) must not contain "c1"
      await(testBackend.get[VirtualUnit](vc2.id))
        .includedUnits.map(_.id) must contain("c1")
    }
  }

  "Cypher operations" should {
    "get a JsValue for a graph item" in new ITestApp {
      val dao = WsCypherService(ws, cache, config)
      val res = await(dao.get(
        """MATCH (n:_Entity) WHERE n.__id = {id} RETURN n.identifier, n.name""",
          Map("id" -> JsString("admin"))))
      // It should return one list value in the data section
      res.data.head.head mustEqual JsString("admin")
    }
  }

  "CypherIdGenerator" should {
    "get the right next ID for repositories" in new ITestApp {
      val idGen = CypherIdGenerator(WsCypherService(ws, cache, config))
      await(idGen.getNextNumericIdentifier(EntityType.Repository, "%06d")) must equalTo("000005")
    }

    "get the right next ID for collections in scope" in new ITestApp {
      // There a 4 collections in the fixtures c1-c4
      // Sigh... - now there's also a fixture named "m19", so the next
      // numeric ID with be "20". I didn't plan this.
      val idGen = CypherIdGenerator(WsCypherService(ws, cache, config))
      await(idGen.getNextChildNumericIdentifier("r1", EntityType.DocumentaryUnit, "c%01d")) must equalTo("c20")
    }
  }
}
