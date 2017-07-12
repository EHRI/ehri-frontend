package integration.portal

import helpers.IntegrationTestRunner
import play.api.test.FakeRequest
import mockdata._
import models.{GuidePage, Guide}


class GuidesSpec extends IntegrationTestRunner {

  private val guideRoutes = controllers.portal.guides.routes.Guides
  private val guideAdminRoutes = controllers.guides.routes.Guides
  private val guidePageAdminRoutes = controllers.guides.routes.GuidePages
  override def getConfig = Map("recaptcha.skip" -> true)


  "Guide views" should {
    "show index page for fixture guide" in new DBTestApp("guide-fixtures.sql") {
      val doc = FakeRequest(guideRoutes.home("terezin")).call()
      status(doc) must equalTo(OK)
    }

    "show 404 for bad path" in new DBTestApp("guide-fixtures.sql") {
      val doc = FakeRequest(guideRoutes.home("BAD")).call()
      status(doc) must equalTo(NOT_FOUND)
    }
    
    val guideData = Map(
      Guide.NAME -> Seq("Hello"),
      Guide.PATH -> Seq("hello"),
      Guide.PICTURE -> Seq("/foo/bar"),
      Guide.VIRTUALUNIT -> Seq("hello"),
      Guide.DESCRIPTION -> Seq("Hello, world"),
      Guide.ACTIVE -> Seq(true.toString)
    )

    "be able to create guides" in new DBTestApp("guide-fixtures.sql") {
      val create = FakeRequest(guideAdminRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(guideData)
      status(create) must equalTo(SEE_OTHER)
      val doc = FakeRequest(GET, redirectLocation(create).get)
        .withUser(privilegedUser).call()
      contentAsString(doc) must contain("Hello")
    }

    "maintain path uniqueness" in new DBTestApp("guide-fixtures.sql") {
      val data = guideData.updated(Guide.PATH, Seq("terezin"))
      val create = FakeRequest(guideAdminRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(create) must equalTo(BAD_REQUEST)
      contentAsString(create) must contain(message("constraints.uniqueness"))
    }

    "maintain path validity" in new DBTestApp("guide-fixtures.sql") {
      val data = guideData.updated(Guide.PATH, Seq("path with space"))
      val create = FakeRequest(guideAdminRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(create) must equalTo(BAD_REQUEST)
      contentAsString(create) must contain(message("guide.path.constraint.validity"))
    }

    "be able to edit guides, including changing the URL" in new DBTestApp("guide-fixtures.sql") {
      val edit = FakeRequest(guideAdminRoutes.editPost("jewishcommunity"))
        .withUser(privilegedUser).withCsrf.callWith(guideData)
      status(edit) must equalTo(SEE_OTHER)
      redirectLocation(edit).get must equalTo(guideAdminRoutes.show("hello").url)
      val doc = FakeRequest(guideAdminRoutes.show("hello"))
        .withUser(privilegedUser).call()
      status(doc) must equalTo(OK)
      contentAsString(doc) must contain("Hello")
    }

    "be able to edit guides, not changing the URL" in new DBTestApp("guide-fixtures.sql") {
      val data = guideData.updated(Guide.PATH, Seq("jewishcommunity"))
      val edit = FakeRequest(guideAdminRoutes.editPost("jewishcommunity"))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(edit) must equalTo(SEE_OTHER)
    }

    "not be able to violate path uniqueness" in new DBTestApp("guide-fixtures.sql") {
      val data = guideData.updated(Guide.PATH, Seq("jewishcommunity"))
      val create = FakeRequest(guideAdminRoutes.createPost())
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(create) must equalTo(BAD_REQUEST)
      contentAsString(create) must contain(message("constraints.uniqueness"))
    }

    "redirect after deleting guides" in  new DBTestApp("guide-fixtures.sql") {
      val del = FakeRequest(guideAdminRoutes.deletePost("jewishcommunity"))
        .withUser(privilegedUser).withCsrf.call()
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

    "be able to create guide pages" in new DBTestApp("guide-fixtures.sql") {
      val create = FakeRequest(guidePageAdminRoutes.createPost("terezin"))
        .withUser(privilegedUser).withCsrf.callWith(pageData)
      status(create) must equalTo(SEE_OTHER)
      val doc = FakeRequest(GET, redirectLocation(create).get)
        .withUser(privilegedUser).call()
      contentAsString(doc) must contain("Blah")
    }

    "be able to edit guide pages, including changing the URL" in new DBTestApp("guide-fixtures.sql") {
      val edit = FakeRequest(guidePageAdminRoutes.editPost("terezin", "places"))
        .withUser(privilegedUser).withCsrf.callWith(pageData)
      status(edit) must equalTo(SEE_OTHER)
      redirectLocation(edit).get must equalTo(guideAdminRoutes.show("terezin").url)
      val doc = FakeRequest(guideAdminRoutes.show("terezin")).withUser(privilegedUser).call()
      status(doc) must equalTo(OK)
      contentAsString(doc) must contain("Blah")
    }

    "not be able to violate path uniqueness" in new DBTestApp("guide-fixtures.sql") {
      val data = pageData.updated(GuidePage.PATH, Seq("keywords"))
      val edit = FakeRequest(guidePageAdminRoutes.editPost("terezin", "places"))
        .withUser(privilegedUser).withCsrf.callWith(data)
      status(edit) must equalTo(BAD_REQUEST)
      contentAsString(edit) must contain(message("constraints.uniqueness"))
    }

    "redirect after deleting guide pages" in  new DBTestApp("guide-fixtures.sql") {
      val del = FakeRequest(guidePageAdminRoutes.deletePost("terezin", "places"))
        .withUser(privilegedUser).withCsrf.call()
      status(del) must equalTo(SEE_OTHER)
      redirectLocation(del).get must equalTo(guideAdminRoutes.show("terezin").url)
    }
  }
}
