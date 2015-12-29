package backend

import defines.{EntityType, ContentTypes, PermissionType}
import play.api.cache.CacheApi
import play.api.inject.guice.GuiceApplicationLoader
import play.api.libs.ws.WSClient
import utils.SystemEventParams.Aggregation
import utils.{RangeParams, RangePage, PageParams, SystemEventParams}
import backend.rest.{CypherIdGenerator, ItemNotFound, ValidationError}
import backend.rest.cypher.CypherDAO
import play.api.libs.json.{JsObject, JsString, Json}
import models.base.AnyModel
import models._
import play.api.test.{WithApplicationLoader, PlaySpecification}
import utils.search.{MockSearchIndexMediator, SearchIndexMediator}
import helpers.RestBackendRunner

import scala.concurrent.ExecutionContext

/**
 * Spec for testing individual data access components work as expected.
 */
class BackendModelSpec extends RestBackendRunner with PlaySpecification {
  sequential

  val userProfile = UserProfile(UserProfileF(id = Some("mike"), identifier = "mike", name = "Mike"))
  val entityType = EntityType.UserProfile
  implicit val apiUser: ApiUser = AuthenticatedUser(userProfile.id)

  import play.api.inject.bind
  val appLoader = new GuiceApplicationLoader(
    new play.api.inject.guice.GuiceApplicationBuilder()
    .configure(RestBackendRunner.backendConfig)
    .overrides(
      bind[SearchIndexMediator].toInstance(mockIndexer),
      bind[EventHandler].toInstance(testEventHandler)
    )
  )

  implicit def config(implicit app: play.api.Application): play.api.Configuration = app.configuration
  implicit def ws(implicit app: play.api.Application): WSClient = app.injector.instanceOf[WSClient]
  implicit def cache(implicit app: play.api.Application): CacheApi = app.injector.instanceOf[CacheApi]
  implicit def execContext(implicit app: play.api.Application): ExecutionContext = app.injector.instanceOf[ExecutionContext]

  val indexEventBuffer = collection.mutable.ListBuffer.empty[String]
  def mockIndexer: SearchIndexMediator = new MockSearchIndexMediator(indexEventBuffer)

  def testBackend(implicit app: play.api.Application, apiUser: ApiUser): BackendHandle =
    app.injector.instanceOf[Backend].withContext(apiUser)

  def testEventHandler = new EventHandler {
    def handleCreate(id: String) = mockIndexer.handle.indexId(id)
    def handleUpdate(id: String) = mockIndexer.handle.indexId(id)
    def handleDelete(id: String) = mockIndexer.handle.clearId(id)
  }

  /**
   * A minimal object that has a resource type and can be read.
   */
  case class TestResource(id: String, data: JsObject) extends backend.WithId
  object TestResource {
    implicit object Resource extends backend.Resource[TestResource] {
      def entityType: EntityType.Value = EntityType.DocumentaryUnit
      import play.api.libs.json._
      import play.api.libs.functional.syntax._
      val restReads: Reads[TestResource] = (
        (__ \ Entity.ID).read[String] and
          (__ \ Entity.DATA).read[JsObject]
        )(TestResource.apply _)
    }
  }

  "RestBackend" should {
    "allow fetching objects" in new WithApplicationLoader(appLoader) {
      val test: TestResource = await(testBackend.get[TestResource]("c1"))
      test.id must equalTo("c1")
    }
  }

  "Generic entity operations" should {
    "not cache invalid responses (as per bug #324)" in new WithApplicationLoader(appLoader) {
      // Previously this would error the second time because the bad
      // response would be cached and error on JSON deserialization.
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
      await(testBackend.get[UserProfile]("invalid-id")) must throwA[ItemNotFound]
    }

    "not error when retrieving existing items at the wrong path (as per bug #500)" in new WithApplicationLoader(appLoader) {
      // Load the item (populating the cache)
      private val doc: DocumentaryUnit = await(testBackend.get[DocumentaryUnit]("c1"))
      // Now try and fetch it under a different resource path...
      await(testBackend.get[UserProfile]("c1")) must throwA[ItemNotFound]
    }

    "get an item by id" in new WithApplicationLoader(appLoader) {
      val profile: UserProfile = await(testBackend.get[UserProfile](userProfile.id))
      profile.id must equalTo(userProfile.id)
    }

    "create an item" in new WithApplicationLoader(appLoader) {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user))
    }

    "use higher characters in log messages" in new WithApplicationLoader(appLoader) {
      val msg = "This is a 日本語メッセージ"
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      await(testBackend.create[UserProfile,UserProfileF](user, logMsg = Some(msg)))
      val history = await(testBackend.history[SystemEvent]("foobar", RangeParams(limit = 1)))
      history.items.size must_== 1
      history.head.size must_== 1
      history.head.head.model.logMessage must_== Some(msg)
    }

    "create an item in (agent) context" in new WithApplicationLoader(appLoader) {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[Repository,DocumentaryUnitF,DocumentaryUnit]("r1", ContentTypes.DocumentaryUnit, doc))
      r.holder must beSome
      r.holder.get.id must equalTo("r1")
      // This triggers an update event for the parent and a create
      // event for the new child
      val events = indexEventBuffer.takeRight(2)
      events.headOption must beSome.which(_ must equalTo("r1"))
      events.lastOption must beSome.which(_ must equalTo("nl-r1-foobar"))
    }

    "create an item in (doc) context" in new WithApplicationLoader(appLoader) {
      val doc = DocumentaryUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
          .createInContext[DocumentaryUnit,DocumentaryUnitF,DocumentaryUnit]("c1", ContentTypes.DocumentaryUnit, doc))
      r.parent must beSome
      r.parent.get.id must equalTo("c1")
    }

    "create an item with additional params" in new WithApplicationLoader(appLoader) {
      val doc = VirtualUnitF(id = None, identifier = "foobar")
      val r = await(testBackend
        .createInContext[VirtualUnit,VirtualUnitF,VirtualUnit]("vc1", ContentTypes.VirtualUnit,
            doc, params = Map("id" -> Seq("c1"))))
      r.includedUnits.headOption must beSome.which { desc =>
        desc.id must equalTo("c1")
      }
    }

    "update an item by id" in new WithApplicationLoader(appLoader) {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val udata = entity.model.copy(location = Some("London"))
      val res = await(testBackend.update[UserProfile,UserProfileF](entity.id, udata))
      res.model.location must equalTo(Some("London"))
    }

    "delete an item by id" in new WithApplicationLoader(appLoader) {
      await(testBackend.delete[UserProfile]("reto"))
      await(testBackend.get[UserProfile]("reto")) must throwA[ItemNotFound]
    }

    "patch an item by id" in new WithApplicationLoader(appLoader) {
      val user = UserProfileF(id = None, identifier = "foobar", name = "Foobar")
      val testVal = "http://example.com/"
      val patchData = Json.obj(UserProfileF.IMAGE_URL -> testVal)
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      val res = await(testBackend.patch[UserProfile](entity.id, patchData))
      res.model.imageUrl must equalTo(Some(testVal))
    }

    "error when creating an item with a non-unique id" in new WithApplicationLoader(appLoader) {
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

    "error when fetching a non-existing item" in new WithApplicationLoader(appLoader) {
      try {
        await(testBackend.get[UserProfile]("blibidyblob"))
        failure("Expected Item not found!")
      } catch {
        case e: ItemNotFound =>
        case _: Throwable => failure("Expected a validation error!")
      }
    }

    "error with context data when deserializing JSON incorrectly" in new WithApplicationLoader(appLoader) {
      try {
        // deliberate use the wrong readable here to generate a
        // deserialization error...
        import backend.Readable
        import play.api.libs.json._
        import play.api.libs.functional.syntax._
        import models.base.Accessor
        import models.json.JsPathExtensions
        val badDeserializer = new ContentType[UserProfile] {
          val restReads: Reads[UserProfile] = (
            __.read[UserProfileF] and
            __.lazyReadSeqOrEmpty(Group.GroupResource.restReads) and
            __.lazyReadSeqOrEmpty(Accessor.Converter.restReads) and
            __.readHeadNullable[SystemEvent] and
            __.read[JsObject]
          )(UserProfile.quickApply _)
          val entityType = UserProfile.UserProfileResource.entityType
          val contentType = UserProfile.UserProfileResource.contentType
        }

        await(testBackend.get[UserProfile]("mike")(badDeserializer))
        failure("Expected BadJson error was not found!")
      } catch {
        case e: backend.rest.BadJson =>
          e.url must beSome.which { url =>
            url must endWith(s"/${UserProfile.UserProfileResource.entityType}/mike")
          }
        case _: Throwable => failure("Expected a json error!")
      }
    }

    "delete an item by id" in new WithApplicationLoader(appLoader) {
      val user = UserProfileF(id = Some("foobar"), identifier = "foo", name = "bar")
      val entity = await(testBackend.create[UserProfile,UserProfileF](user))
      await(testBackend.delete[UserProfile](entity.id))
    }

    "page items" in new WithApplicationLoader(appLoader) {
      val r = await(testBackend.list[UserProfile]())
      r.items.length mustEqual 5
      r.page mustEqual 1
      r.limit mustEqual backend.rest.Constants.DEFAULT_LIST_LIMIT
      r.total mustEqual 5
    }

    "count items" in new WithApplicationLoader(appLoader) {
      var r = await(testBackend.count[UserProfile]())
      r mustEqual 5L
    }

    "count child items" in new WithApplicationLoader(appLoader) {
      var r = await(testBackend.countChildren[DocumentaryUnit]("c1"))
      r mustEqual 1L
    }

    "emit appropriate signals" in new WithApplicationLoader(appLoader) {
    }
  }

  "PermissionDAO" should {
    "be able to fetch user's own permissions" in new WithApplicationLoader(appLoader) {
      val perms = await(testBackend.getGlobalPermissions(userProfile.id))
      userProfile.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Create) must beSome
    }

    "be able to add and revoke a user's global permissions" in new WithApplicationLoader(appLoader) {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val perms = await(testBackend.getGlobalPermissions(user.id))
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

    "be able to set a user's permissions within a scope" in new WithApplicationLoader(appLoader) {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      val data = Map(ContentTypes.DocumentaryUnit.toString -> List(
        PermissionType.Update.toString,
        PermissionType.Delete.toString)
      )
      val perms = await(testBackend.getScopePermissions(user.id, "r1"))
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Update) must beNone
      user.getPermission(perms, ContentTypes.DocumentaryUnit, PermissionType.Delete) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Create) must beNone
      user.getPermission(perms, ContentTypes.Repository, PermissionType.Update) must beNone
      await(testBackend.setScopePermissions(user.id, "r1", data))
      // Since c1 is held by r1, we should now have permissions to update and delete c1.
      val newItemPerms = await(testBackend.getItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      user.getPermission(newItemPerms, PermissionType.Update) must beSome
      user.getPermission(newItemPerms, PermissionType.Delete) must beSome
    }

    "be able to set a user's permissions for an item" in new WithApplicationLoader(appLoader) {
      val user = UserProfile(UserProfileF(id = Some("reto"), identifier = "reto", name = "Reto"))
      // NB: Currently, there's already a test permission grant for Reto-create on c1...
      val data = List(PermissionType.Update.toString, PermissionType.Delete.toString)
      val perms = await(testBackend.getItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1"))
      user.getPermission(perms, PermissionType.Update) must beNone
      user.getPermission(perms, PermissionType.Delete) must beNone
      val newItemPerms = await(testBackend.setItemPermissions(user.id, ContentTypes.DocumentaryUnit, "c1", data))
      user.getPermission(newItemPerms, PermissionType.Update) must beSome
      user.getPermission(newItemPerms, PermissionType.Delete) must beSome
    }

    "be able to list permissions" in new WithApplicationLoader(appLoader) {
      val page = await(testBackend.listScopePermissionGrants[PermissionGrant]("r1", PageParams.empty))
      page.items must not(beEmpty)
    }
  }

  "VisibilityDAO" should {
    "set visibility correctly" in new WithApplicationLoader(appLoader) {
      // First, fetch an object and assert its accessibility
      val c1a = await(testBackend.get[DocumentaryUnit]("c1"))
      c1a.accessors.map(_.id) must containAllOf(List("admin", "mike"))

      val set = await(testBackend.setVisibility[DocumentaryUnit](c1a.id, List("mike", "reto", "admin")))
      val c1b = await(testBackend.get[DocumentaryUnit]("c1"))
      c1b.accessors.map(_.id) must containAllOf(List("admin", "mike", "reto"))
    }

    "promote and demote items" in new WithApplicationLoader(appLoader) {
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
    "handle paging correctly" in new WithApplicationLoader(appLoader) {
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

    "get activity for a user" in new WithApplicationLoader(appLoader) {
      await(testBackend.patch[DocumentaryUnit]("c1", Json.obj("prop1" -> "foo")))
      val forUser: RangePage[Seq[SystemEvent]] =
        await(testBackend.listEventsForUser[SystemEvent](userProfile.id, RangeParams.empty.withoutLimit))
      forUser.size must equalTo(1)

      val byUser: RangePage[Seq[SystemEvent]] =
        await(testBackend.listUserActions[SystemEvent](userProfile.id, RangeParams.empty.withoutLimit))
      byUser.size must equalTo(1)
    }
  }

  "Social operations" should {
    "allow following and unfollowing" in new WithApplicationLoader(appLoader) {
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

    "allow watching and unwatching" in new WithApplicationLoader(appLoader) {
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
      await(testBackend.watch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beTrue
      val watching = await(testBackend.watching[AnyModel](userProfile.id))
      watching.exists(_.id == "c1") must beTrue
      val watchingPage = await(testBackend.watching[AnyModel](userProfile.id))
      watchingPage.total must equalTo(1)
      await(testBackend.unwatch(userProfile.id, "c1"))
      await(testBackend.isWatching(userProfile.id, "c1")) must beFalse
    }

    "allow blocking and unblocking" in new WithApplicationLoader(appLoader) {
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

    "handle virtual collections (and bookmarks)" in new WithApplicationLoader(appLoader) {
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
    "get a JsValue for a graph item" in new WithApplicationLoader(appLoader) {
      val dao = new CypherDAO
      val res = await(dao.cypher(
        """MATCH (n:_Entity) WHERE n.__id = {id} RETURN n.identifier, n.name""",
          Map("id" -> JsString("admin"))))
      // It should return one list value in the data section
      val list = (res \ "data").as[List[List[String]]]
      list.head.head mustEqual "admin"
    }
  }

  "CypherIdGenerator" should {
    "get the right next ID for repositories" in new WithApplicationLoader(appLoader) {
      val idGen = new CypherIdGenerator(new CypherDAO)
      await(idGen.getNextNumericIdentifier(EntityType.Repository, "%06d")) must equalTo("000005")
    }

    "get the right next ID for collections in scope" in new WithApplicationLoader(appLoader) {
      // There a 4 collections in the fixtures c1-c4
      // Sigh... - now there's also a fixture named "m19", so the next
      // numeric ID with be "20". I didn't plan this.
      val idGen = new CypherIdGenerator(new CypherDAO)
      await(idGen.getNextChildNumericIdentifier("r1", EntityType.DocumentaryUnit, "c%01d")) must equalTo("c20")
    }
  }
}
