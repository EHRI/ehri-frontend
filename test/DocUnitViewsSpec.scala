package test

import helpers.{formPostHeaders,Neo4jRunnerSpec}
import models._
import play.api.test.Helpers._
import defines._
import controllers.routes
import controllers.ListParams
import play.api.i18n.Messages
import play.api.test.FakeRequest
import play.api.http.{MimeTypes, HeaderNames}


class DocUnitViewsSpec extends Neo4jRunnerSpec(classOf[DocUnitViewsSpec]) {
  import mocks.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.profile_id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "DocumentaryUnit views" should {

    "list should get some (world-readable) items" in new FakeApp {
      val list = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.list.url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(oneItemHeader)
      contentAsString(list) must not contain ("c1")
      contentAsString(list) must contain("c4")
    }

    "list when logged in should get more items" in new FakeApp {
      val list = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.list.url)).get
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("c1")
      contentAsString(list) must contain("c2")
      contentAsString(list) must contain("c3")
      contentAsString(list) must contain("c4")
    }

    "search should find some items" in new FakeApp {
      val search = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.search.url)).get
      status(search) must equalTo(OK)
      contentAsString(search) must contain(multipleItemsHeader)
      contentAsString(search) must contain("c1")
      contentAsString(search) must contain("c2")
      contentAsString(search) must contain("c3")
      contentAsString(search) must contain("c4")
    }

    "link to other privileged actions when logged in" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.update("c1").url)
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.delete("c1").url)
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.createDoc("c1").url)
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.visibility("c1").url)
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.search().url)
    }

    "link to holder" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain(controllers.archdesc.routes.Repositories.get("r1").url)
    }

    "link to holder when a child item" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c2").url)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain(controllers.archdesc.routes.Repositories.get("r1").url)
    }

    "give access to c1 when logged in" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c1").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain("c1")
    }

    "deny access to c1 when logged in as an ordinary user" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c2").url)).get
      status(show) must equalTo(UNAUTHORIZED)
      contentAsString(show) must not contain ("Collection 2")
    }

    "allow deleting c4 when logged in" in new FakeApp {
      val del = route(fakeLoggedInHtmlRequest(privilegedUser, POST, controllers.archdesc.routes.DocumentaryUnits.deletePost("c4").url)).get
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
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      println(contentAsString(cr))
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("Held By")
      // After having created an item it should contain a 'history' pane
      // on the show page
      contentAsString(show) must contain(controllers.archdesc.routes.DocumentaryUnits.history("nl-r1-hello-kitty").url)
    }

    "give a form error when creating items with the same id as existing ones" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "publicationStatus" -> Seq("Published")
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val call = fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*)
      val cr1 = route(call, testData).get
      status(cr1) must equalTo(SEE_OTHER)
      // okay the first time
      val cr2 = route(call, testData).get
      status(cr2) must equalTo(BAD_REQUEST)
      // NB: This error string comes from the server, so might
      // not match if changed there - single quotes surround the value
      contentAsString(cr2) must contain("exists and must be unique")
    }

    "give a form error when saving an item with with a bad date" in new FakeApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("c1"),
        "descriptions[0].dates[0].startDate" -> Seq("1945-01-01"),
        "descriptions[0].dates[0].endDate" -> Seq("1945-12-32") // BAD!
      )
      // Since the item id is derived from the identifier field,
      // a form error should result from using the same identifier
      // twice within the given scope (in this case, r1)
      val call = fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Repositories.createDocPost("r1").url).withHeaders(formPostHeaders.toSeq: _*)
      val cr = route(call, testData).get
      status(cr) must equalTo(BAD_REQUEST)
      // If we were doing validating dates we'd use:
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
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updatePost("c1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET, redirectLocation(cr).get)).get
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
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updatePost("c1").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(SEE_OTHER)

      // Get the item history page and check the message is there...
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.history("c1").url)).get
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

      val cr = route(fakeLoggedInHtmlRequest(unprivilegedUser, POST,
        controllers.archdesc.routes.DocumentaryUnits.updatePost("c4").url).withHeaders(formPostHeaders.toSeq: _*), testData).get
      status(cr) must equalTo(UNAUTHORIZED)

      // We can view the item when not logged in...
      val show = route(fakeLoggedInHtmlRequest(unprivilegedUser, GET, controllers.archdesc.routes.DocumentaryUnits.get("c4").url)).get
      status(show) must equalTo(OK)
      contentAsString(show) must not contain ("New Content for c4")
    }

    "should redirect to login page when permission denied when not logged in" in new FakeApp {
      val show = route(FakeRequest(GET, controllers.archdesc.routes.DocumentaryUnits.get("c1").url)
        .withHeaders(HeaderNames.ACCEPT -> MimeTypes.HTML)).get
      status(show) must equalTo(SEE_OTHER)
    }

    "show history when logged in as privileged user" in new FakeApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.history("c1").url)).get
      status(show) must equalTo(OK)
    }
  }

  step {
    runner.stop()
  }
}
