package test

import helpers._
import models.{GroupF, GroupMeta, UserProfileF, UserProfileMeta}
import controllers.routes
import play.api.test._
import play.api.test.Helpers._
import defines._

/**
 * Created by mike on 05/06/13.
 */
class RepositoryViewsSpec extends Neo4jRunnerSpec(classOf[EntityViewsSpec]) {
  import mocks.UserFixtures.{privilegedUser,unprivilegedUser}

  // Mock user who belongs to admin
  val userProfile = UserProfileMeta(
    model = UserProfileF(id = Some(privilegedUser.profile_id), identifier = "test", name="test user"),
    groups = List(GroupMeta(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "Repository views" should {

    val COUNTRY = "nl"

    "list should get some items" in new FakeApp {
      val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.list().url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("r1")
      contentAsString(list) must contain("r2")
    }

    "search should get some items" in new FakeApp {
      val list = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.search().url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("r1")
      contentAsString(list) must contain("r2")
    }

    "allow creating new items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("wiener-library"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Wiener Library"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("Some history"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("Some content"),
        "descriptions[0].addressArea[0].addressName" -> Seq("An Address"),
        "descriptions[0].addressArea[0].telephone[0]" -> Seq("12345 546395"),
        "descriptions[0].controlArea[0].sources[0]" -> Seq("ClaimsCon"),
        "descriptions[0].controlArea[0].sources[1]" -> Seq("YV"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Countries.createRepositoryPost(COUNTRY).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      // FIXME: This route will change when a property ID mapping scheme is devised
      val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Some history")
      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("An Address")
      contentAsString(show) must contain("12345 546395")
    }

    "error if missing mandatory values" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Countries.createRepositoryPost(COUNTRY).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(BAD_REQUEST)
    }

    "give a form error when creating items with an existing identifier" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1")
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Countries.createRepositoryPost(COUNTRY).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)
      val cr2 = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Countries.createRepositoryPost(COUNTRY).url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr2) must equalTo(BAD_REQUEST)
    }


    "link to other privileged actions when logged in" in new FakeApp {
      val show = route(fakeLoggedInRequest(privilegedUser, GET, routes.Repositories.get("r1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(routes.Repositories.update("r1").url)
      contentAsString(show) must contain(routes.Repositories.delete("r1").url)
      contentAsString(show) must contain(routes.Repositories.createDoc("r1").url)
      contentAsString(show) must contain(routes.Repositories.visibility("r1").url)
      contentAsString(show) must contain(routes.Repositories.search().url)
    }

    "allow updating items when logged in as privileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Repository 1"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("New History for r1"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("New Content for r1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = route(fakeLoggedInRequest(privilegedUser, POST,
        routes.Repositories.updatePost("r1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for r1")
    }

    "disallow updating items when logged in as unprivileged user" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].name" -> Seq("Repository 1"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("New Content for r1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = route(fakeLoggedInRequest(unprivilegedUser, POST,
        routes.Repositories.updatePost("r1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // We can view the item when not logged in...
      val show = route(fakeLoggedInRequest(unprivilegedUser, GET, routes.Repositories.get("r1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must not contain ("New Content for r1")
    }
  }
}
