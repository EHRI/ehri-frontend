package integration

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import play.api.test.Helpers._
import defines._
import controllers.routes


class DocUnitLinkAnnotateSpec extends Neo4jRunnerSpec(classOf[DocUnitLinkAnnotateSpec]) {
  import mocks.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "DocumentaryUnit views" should {

    "allow linking to items via annotation" in new FakeApp {
      val testItem = "c1"
      val linkSrc = "cvocc1"
      val body = "This is a link"
      val testData: Map[String, Seq[String]] = Map(
        LinkF.LINK_TYPE -> Seq(LinkF.LinkType.Associative.toString),
        LinkF.DESCRIPTION -> Seq(body)
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.linkAnnotatePost(testItem, EntityType.Concept, linkSrc).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
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
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.linkMultiAnnotatePost(testItem).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
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
        "identityArea.name" -> Seq("A Second Description"),
        "contentArea.scopeAndContent" -> Seq("This is a second description")
      )
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.createDescriptionPost(testItem).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain("This is a second description")
      mockIndexer.eventBuffer.last must equalTo("c1")
    }

    "allow updating individual descriptions" in new FakeApp {
      val testItem = "c1"
      val testItemDesc = "cd1"
      val testData: Map[String, Seq[String]] = Map(
        "languageCode" -> Seq("en"),
        "id" -> Seq("cd1"),
        "identityArea.name" -> Seq("An Updated Description"),
        "contentArea.scopeAndContent" -> Seq("This is an updated description")
      )
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updateDescriptionPost(testItem, testItemDesc).url)
        .withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must contain("This is an updated description")
      contentAsString(getR) must not contain ("Some description text for c1")
      mockIndexer.eventBuffer.last must equalTo("c1")
    }

    "allow deleting individual descriptions" in new FakeApp {
      val testItem = "c1"
      val testItemDesc = "cd1-2"
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.deleteDescriptionPost(testItem, testItemDesc).url)
        .withHeaders(formPostHeaders.toSeq: _*)).get
      status(cr) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(getR) must equalTo(OK)
      contentAsString(getR) must not contain ("Some alternate description text for c1")
      mockIndexer.eventBuffer.last must equalTo("cd1-2")
    }
  }
}
