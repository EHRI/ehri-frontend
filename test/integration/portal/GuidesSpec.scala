package integration.portal

import helpers.{WithSqlFile,IntegrationTestRunner}
import play.api.i18n.Messages
import play.api.test.FakeRequest
import mocks._
import models.{GuidePage, Guide}


class GuidesSpec extends IntegrationTestRunner {

  private val guideRoutes = controllers.portal.guides.routes.Guides
  private val guideAdminRoutes = controllers.guides.routes.Guides
  private val guidePageAdminRoutes = controllers.guides.routes.GuidePages

  override def getConfig = Map("recaptcha.skip" -> true)

  "Guide views" should {
    "show index page for fixture guide" in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val doc = route(FakeRequest(GET, guideRoutes.home("terezin").url)).get
      status(doc) must equalTo(OK)
    }

    "show 404 for bad path" in new WithSqlFile("guide-fixtures.sql") {
      val doc = route(FakeRequest(GET, guideRoutes.home("BAD").url)).get
      status(doc) must equalTo(NOT_FOUND)
    }

    

    val guideData = Map(
      Guide.NAME -> Seq("Hello"),
      Guide.PATH -> Seq("hello"),
      Guide.PICTURE -> Seq("/foo/bar"),
      Guide.VIRTUALUNIT -> Seq("hello"),
      Guide.DESCRIPTION -> Seq("Hello, world"),
      Guide.DEFAULT -> Seq("0"),
      Guide.ACTIVE -> Seq(true.toString)
    )

    "be able to create guides"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.createPost().url), guideData).get
      status(create) must equalTo(SEE_OTHER)
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        redirectLocation(create).get)).get
      contentAsString(doc) must contain("Hello")
    }

    "maintain path uniqueness"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val data = guideData.updated(Guide.PATH, Seq("terezin"))
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.createPost().url), data).get
      status(create) must equalTo(BAD_REQUEST)
      contentAsString(create) must contain(Messages("constraints.uniqueness"))
    }

    "be able to edit guides, including changing the URL"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val edit = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.editPost("jewishcommunity").url), guideData).get
      status(edit) must equalTo(SEE_OTHER)
      redirectLocation(edit).get must equalTo(guideAdminRoutes.show("hello").url)
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        guideAdminRoutes.show("hello").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must contain("Hello")
    }

    "be able to edit guides, not changing the URL"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val data = guideData.updated(Guide.PATH, Seq("jewishcommunity"))
      val edit = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.editPost("jewishcommunity").url), data).get
      status(edit) must equalTo(SEE_OTHER)
    }

    "not be able to violate path uniqueness"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val data = guideData.updated(Guide.PATH, Seq("jewishcommunity"))
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.createPost().url), data).get
      status(create) must equalTo(BAD_REQUEST)
      contentAsString(create) must contain(Messages("constraints.uniqueness"))
    }

    "redirect after deleting guides" in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val del = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guideAdminRoutes.deletePost("jewishcommunity").url)).get
      status(del) must equalTo(SEE_OTHER)
      redirectLocation(del).get must equalTo(guideAdminRoutes.list().url)
    }

    val pageData = Map(
      GuidePage.NAME -> Seq("Blah"),
      GuidePage.PATH -> Seq("blah"),
      GuidePage.POSITION -> Seq(GuidePage.MenuPosition.Side.toString),
      GuidePage.LAYOUT -> Seq(GuidePage.Layout.Map.toString),
      GuidePage.CONTENT -> Seq("blah")
    )

    "be able to create guide pages"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val create = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guidePageAdminRoutes.createPost("terezin").url), pageData).get
      status(create) must equalTo(SEE_OTHER)
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        redirectLocation(create).get)).get
      contentAsString(doc) must contain("Blah")
    }

    "be able to edit guide pages, including changing the URL"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val edit = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guidePageAdminRoutes.editPost("terezin", "places").url), pageData).get
      status(edit) must equalTo(SEE_OTHER)
      redirectLocation(edit).get must equalTo(guideAdminRoutes.show("terezin").url)
      val doc = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        guideAdminRoutes.show("terezin").url)).get
      status(doc) must equalTo(OK)
      contentAsString(doc) must contain("Blah")
    }

    "not be able to violate path uniqueness"  in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val data = pageData.updated(GuidePage.PATH, Seq("keywords"))
      val edit = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guidePageAdminRoutes.editPost("terezin", "places").url), data).get
      status(edit) must equalTo(BAD_REQUEST)
      contentAsString(edit) must contain(Messages("constraints.uniqueness"))
    }

    "redirect after deleting guide pages" in new WithSqlFile("guide-fixtures.sql", fakeApplication()) {
      val del = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        guidePageAdminRoutes.deletePost("terezin", "places").url)).get
      status(del) must equalTo(SEE_OTHER)
      redirectLocation(del).get must equalTo(guideAdminRoutes.show("terezin").url)
    }
  }
}
