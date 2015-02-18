package integration

import helpers.{TestHelpers, IntegrationTestRunner}
import models._


class VirtualUnitViewsSpec extends IntegrationTestRunner with TestHelpers {
  import mocks.{privilegedUser, unprivilegedUser}

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )
  
  val vuRoutes = controllers.virtual.routes.VirtualUnits

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "VirtualUnit views" should {

    "search should find some items" in new ITestApp {
      val search = route(fakeLoggedInHtmlRequest(privilegedUser, vuRoutes.search())).get
      status(search) must equalTo(OK)
      contentAsString(search) must contain(multipleItemsHeader)
      contentAsString(search) must contain("vu1")
    }

    "link to other privileged actions when logged in" in new ITestApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser, vuRoutes.get("vu1"))).get
      status(show) must equalTo(OK)
      contentAsString(show) must contain(vuRoutes.update("vu1").url)
      contentAsString(show) must contain(vuRoutes.delete("vu1").url)
      contentAsString(show) must contain(vuRoutes.createChild("vu1").url)
      contentAsString(show) must contain(vuRoutes.createChildRef("vu1").url)
      contentAsString(show) must contain(vuRoutes.visibility("vu1").url)
      contentAsString(show) must contain(vuRoutes.search().url)
    }

    "link to holder when a child item" in new ITestApp {
      val show = route(fakeLoggedInHtmlRequest(privilegedUser,
          vuRoutes.getInVc("vc1,vu1", "vu2"))).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain(vuRoutes.get("vc1").url)
    }

    "allow creating new items with owned descriptions" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("hello-kitty"),
        "descriptions[0].languageCode" -> Seq("en"),
        "descriptions[0].identityArea.name" -> Seq("Hello Kitty"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Some content"),
        "descriptions[0].identityArea.dates[0].startDate" -> Seq("1939-01-01"),
        "descriptions[0].identityArea.dates[0].endDate" -> Seq("1945-01-01"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser,
        vuRoutes.createChildPost("vc1")), testData).get
      status(cr) must equalTo(SEE_OTHER)
      redirectLocation(cr) must equalTo(Some(vuRoutes.getInVc("vc1", "hello-kitty").url))

      val show = route(fakeLoggedInHtmlRequest(privilegedUser,
        vuRoutes.getInVc("vc1", "hello-kitty"))).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("Virtual Collection 1")
      // After having created an item it should contain a 'history' pane
      // on the show page
      contentAsString(show) must contain(vuRoutes.history("hello-kitty").url)
      indexEventBuffer.last must equalTo("hello-kitty")
    }

    "allow creating new items with included units" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("hello-kitty"),
        VirtualUnitF.INCLUDE_REF -> Seq("c1")
      )
      val cr = route(fakeLoggedInHtmlRequest(privilegedUser,
        vuRoutes.createChildRefPost("vc1")), testData).get
      status(cr) must equalTo(SEE_OTHER)

      val show = route(fakeLoggedInHtmlRequest(privilegedUser, vuRoutes.getInVc("vc1", "c1"))).get
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some description text for c1")
      contentAsString(show) must contain("Virtual Collection 1")
    }
  }
}
