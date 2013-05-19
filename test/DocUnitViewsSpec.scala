package test

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import models.base.Accessor
import play.api.test.Helpers._
import defines._
import controllers.routes
import controllers.ListParams
import play.api.i18n.Messages
import play.api.test.FakeRequest


class DocUnitViewsSpec extends Neo4jRunnerSpec(classOf[DocUnitViewsSpec]) {
  import mocks.UserFixtures.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(Entity.fromString(privilegedUser.profile_id, EntityType.UserProfile)
    .withRelation(Accessor.BELONGS_REL, Entity.fromString("admin", EntityType.Group)))

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "DocumentaryUnit views" should {

    "list should get some (world-readable) items" in new FakeApp {
      val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.DocumentaryUnits.list.url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(oneItemHeader)
      contentAsString(list) must not contain ("c1")
      contentAsString(list) must contain("c4")
    }

    "list when logged in should get more items" in new FakeApp {
      val list = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.list.url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("c1")
      contentAsString(list) must contain("c2")
      contentAsString(list) must contain("c3")
      contentAsString(list) must contain("c4")
    }

    "search should find some items" in new FakeApp {
      val search = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.search.url)).get
      status(search) must equalTo(OK)
      contentAsString(search) must contain(multipleItemsHeader)
      contentAsString(search) must contain("c1")
      contentAsString(search) must contain("c2")
      contentAsString(search) must contain("c3")
      contentAsString(search) must contain("c4")
    }

    "list when logged with identifier filter in should get one" in new FakeApp {
      val params = s"${ListParams.PROPERTY_NAME}[0]=identifier&${ListParams.PROPERTY_VALUE}[0]=c3"
      val list = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.list.url + s"?$params")).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(oneItemHeader)
      contentAsString(list) must contain("c3")
    }

    "link to other privileged actions when logged in" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(routes.DocumentaryUnits.update("c1").url)
      contentAsString(show) must contain(routes.DocumentaryUnits.delete("c1").url)
      contentAsString(show) must contain(routes.DocumentaryUnits.createDoc("c1").url)
      contentAsString(show) must contain(routes.DocumentaryUnits.visibility("c1").url)
      contentAsString(show) must contain(routes.DocumentaryUnits.search().url)
    }

    "link to holder" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain(routes.Repositories.get("r1").url)
    }

    "link to holder when a child item" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.get("c2").url)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain(routes.Repositories.get("r1").url)
    }

    "give access to c1 when logged in" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("c1")
    }

    "deny access to c1 when logged in as an ordinary user" in new FakeApp {
      val show = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.DocumentaryUnits.get("c2").url)).get
      status(show) must equalTo(UNAUTHORIZED)
      contentAsString(show) must not contain ("Collection 2")
    }

    "allow deleting c4 when logged in" in new FakeApp {
      val del = route(fakeLoggedInRequest(privilegedUser, POST, routes.DocumentaryUnits.deletePost("c4").url)).get
      status(del) must equalTo(SEE_OTHER)
    }

    "allow creating new items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("hello-kitty"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Hello Kitty"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Some content"),
        "descriptions[0].dates[0].startDate" -> Seq("1939-01-01"),
        "descriptions[0].dates[0].endDate" -> Seq("1945-01-01"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        controllers.routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      println(contentAsString(cr))
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("Held By")
      // After having created an item it should contain a 'history' pane
      // on the show page
      contentAsString(show) must contain(routes.DocumentaryUnits.history("nl-r1-hello-kitty").url)
    }

    "give a form error when creating items with the same id as existing ones" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "publicationStatus" -> Seq("Published")
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val call = fakeLoggedInRequest(privilegedUser, POST,
        routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*)
      val cr1 = route(call, testData).get
      status(cr1) must equalTo(SEE_OTHER)
      // okay the first time
      val cr2 = route(call, testData).get
      status(cr2) must equalTo(BAD_REQUEST)
      // NB: This error string comes from the server, so might
      // not match if changed there - single quotes surround the value
      contentAsString(cr2) must contain("exists and must be unique")
    }

    "give a form error when saving an invalid date" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].dates[0].startDate" -> Seq("1945-01-01"),
        "descriptions[0].dates[0].endDate" -> Seq("1945-13-32") // THIS SHOULD FAIL!
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val call = fakeLoggedInRequest(privilegedUser, POST,
        routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*)
      val cr = route(call, testData).get
      status(cr) must equalTo(BAD_REQUEST)
      // NB: This error string comes from the server, so might
      // not match if changed there - single quotes surround the value
      contentAsString(cr) must contain(Messages("error.date"))
    }

    "allow updating items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Collection 1"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c1"),
        "descriptions[0].contextArea.acquistition" -> Seq("Acquisistion info"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.updatePost("c1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for c1")
    }

    "allow updating an item with a custom log message" in new FakeApp {
      val msg = "Imma updating this item!"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Collection 1 - Updated"),
        "logMessage" -> Seq(msg)
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.updatePost("c1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      // Log message should be in the history section...
      contentAsString(show) must contain(msg)
    }

    "disallow updating items when logged in as unprivileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c4"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Collection 4"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("New Content for c4"),
        "publicationStatus" -> Seq("Draft")
      )

      val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.DocumentaryUnits.updatePost("c4").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // We can view the item when not logged in...
      val show = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.DocumentaryUnits.get("c4").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must not contain ("New Content for c4")
    }

    "should redirect to login page when permission denied when not logged in" in new FakeApp {
      val show = route(FakeRequest(GET, routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(SEE_OTHER)
    }

    "show history when logged in as privileged user" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET,
        routes.DocumentaryUnits.history("c1").url)).get
      status(show) must equalTo(OK)
    }

    "allow granting permissions to create a doc within the scope of r2" in new FakeApp {

      import ContentType._

      val testRepo = "r2"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Test Item"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.Repositories.createDocPost("r2").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // Grant permissions to create docs within the scope of r2
      val permTestData: Map[String, List[String]] = Map(
        DocumentaryUnit.toString -> List("create", "update", "delete")
      )
      val permReq = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Repositories.setScopedPermissionsPost(testRepo, ContentType.UserProfile, unprivilegedUser.profile_id).url)
        .withHeaders(formPostHeaders.toSeq: _*), permTestData).get
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again and create the item... it should succeed.
      // Check we cannot create an item...
      val cr2 = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.Repositories.createDocPost(testRepo).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(unprivilegedUser, GET, redirectLocation(cr2).get)).get
      status(getR) must equalTo(OK)
    }

    "allow granting permissions on a specific item" in new FakeApp {

      import ContentType._

      val testItem = "c4"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq(testItem),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Changed Name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // Grant permissions to update item c1
      val permTestData: Map[String, List[String]] = Map(
        DocumentaryUnit.toString -> List("update")
      )
      val permReq = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.setItemPermissionsPost(testItem, ContentType.UserProfile, unprivilegedUser.profile_id).url)
        .withHeaders(formPostHeaders.toSeq: _*), permTestData).get
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr2 = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(unprivilegedUser, GET, redirectLocation(cr2).get)).get
      status(getR) must equalTo(OK)
    }

    "allow commenting via annotations" in new FakeApp {
      val testItem = "c1"
      val body = "This is a neat annotation"
      val testData: Map[String, Seq[String]] = Map(
        AnnotationF.ANNOTATION_TYPE -> Seq(AnnotationF.AnnotationType.Comment.toString),
        AnnotationF.BODY -> Seq(body)
      )
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.annotatePost(testItem).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain(body)
    }

    "allow linking to items via annotation" in new FakeApp {
      val testItem = "c1"
      val linkSrc = "cvocc1"
      val body = "This is a link"
      val testData: Map[String, Seq[String]] = Map(
        LinkF.LINK_TYPE -> Seq(LinkF.LinkType.Associative.toString),
        LinkF.DESCRIPTION -> Seq(body)
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.linkAnnotatePost(testItem, EntityType.Concept.toString, linkSrc).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain(linkSrc)
      contentAsString(getR) must contain(body)
    }

    "allow linking to multiple items via a single form submission" in new FakeApp {
      val testItem = "c1"
      val body1 = "This is a link 1"
      val body2 = "This is a link 2"
      val testData: Map[String, Seq[String]] = Map(
        "link[0].id" -> Seq("c2"),
        "link[0].data." + LinkF.LINK_TYPE -> Seq(LinkF.LinkType.Associative.toString),
        "link[0].data." + LinkF.DESCRIPTION -> Seq(body1),
        "link[1].id" -> Seq("c3"),
        "link[1].data." + LinkF.LINK_TYPE -> Seq(LinkF.LinkType.Associative.toString),
        "link[1].data." + LinkF.DESCRIPTION -> Seq(body2)
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.linkMultiAnnotatePost(testItem).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain("c2")
      contentAsString(getR) must contain(body1)
      contentAsString(getR) must contain("c3")
      contentAsString(getR) must contain(body2)
    }

    "allow adding extra descriptions" in new FakeApp {
      val testItem = "c1"
      val testData: Map[String, Seq[String]] = Map(
        "languageCode" -> Seq("en"),
        "name" -> Seq("A Second Description"),
        "contentArea.scopeAndContent" -> Seq("This is a second description")
      )
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.createDescriptionPost(testItem).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain("This is a second description")
    }

    "allow updating individual descriptions" in new FakeApp {
      val testItem = "c1"
      val testItemDesc = "cd1"
      val testData: Map[String, Seq[String]] = Map(
        "languageCode" -> Seq("en"),
        "id" -> Seq("cd1"),
        "name" -> Seq("An Updated Description"),
        "contentArea.scopeAndContent" -> Seq("This is an updated description")
      )
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.updateDescriptionPost(testItem, testItemDesc).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain("This is an updated description")
      contentAsString(getR) must not contain ("Some description text for c1")
    }

    "allow deleting individual descriptions" in new FakeApp {
      val testItem = "c1"
      val testItemDesc = "cd1-2"
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.DocumentaryUnits.deleteDescriptionPost(testItem, testItemDesc).url)
        .withHeaders(formPostHeaders.toSeq: _*)).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must not contain ("Some alternate description text for c1")
    }
  }

  step {
    runner.stop
  }
}
