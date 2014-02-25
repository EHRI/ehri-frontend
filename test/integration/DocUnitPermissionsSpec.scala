package integration

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import play.api.test.Helpers._
import defines._
import controllers.routes


class DocUnitPermissionsSpec extends Neo4jRunnerSpec(classOf[DocUnitPermissionsSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  "DocumentaryUnit views" should {

    "allow granting permissions to create a doc within the scope of r2" in new FakeApp {

      import ContentTypes._

      val testRepo = "r2"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("test"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("Test Item"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.archdesc.routes.Repositories.createDocPost("r2").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // Grant permissions to create docs within the scope of r2
      val permTestData: Map[String, List[String]] = Map(
        DocumentaryUnit.toString -> List("create", "update", "delete")
      )
      val permReq = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Repositories.setScopedPermissionsPost(testRepo, EntityType.UserProfile, unprivilegedUser.id).url)
        .withHeaders(formPostHeaders.toSeq: _*), permTestData).get
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again and create the item... it should succeed.
      // Check we cannot create an item...
      val cr2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.archdesc.routes.Repositories.createDocPost(testRepo).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET, redirectLocation(cr2).get)).get
      status(getR) must equalTo(OK)
    }

    "allow granting permissions on a specific item" in new FakeApp {

      import ContentTypes._

      val testItem = "c4"
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq(testItem),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("Changed Name"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("A test"),
        "publicationStatus" -> Seq("Draft")
      )

      // Trying to create the item should fail initially.
      // Check we cannot create an item...
      val cr = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // Grant permissions to update item c1
      val permTestData: Map[String, List[String]] = Map(
        DocumentaryUnit.toString -> List("update")
      )
      val permReq = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.setItemPermissionsPost(testItem, EntityType.UserProfile, unprivilegedUser.id).url)
        .withHeaders(formPostHeaders.toSeq: _*), permTestData).get
      status(permReq) must equalTo(SEE_OTHER)
      // Now try again to update the item, which should succeed
      // Check we can update the item
      val cr2 = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updatePost(testItem).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(SEE_OTHER)
      val getR = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET, redirectLocation(cr2).get)).get
      status(getR) must equalTo(OK)
    }
  }
}
