package integration.admin

import helpers.IntegrationTestRunner
import models._
import play.api.test.FakeRequest
import services.data.{ApiUser, AuthenticatedUser}


class VirtualUnitViewsSpec extends IntegrationTestRunner {
  import mockdata.privilegedUser

  val vuRoutes = controllers.virtual.routes.VirtualUnits

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  "VirtualUnit views" should {

    "search should find some items" in new ITestApp {
      val search = FakeRequest(vuRoutes.search()).withUser(privilegedUser).call()
      status(search) must equalTo(OK)
      contentAsString(search) must contain(multipleItemsHeader)
      contentAsString(search) must contain("vu1")
    }

    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(vuRoutes.get("vu1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(vuRoutes.update("vu1").url)
      contentAsString(show) must contain(vuRoutes.delete("vu1").url)
      contentAsString(show) must contain(vuRoutes.createChild("vu1").url)
      contentAsString(show) must contain(vuRoutes.createChildRef("vu1").url)
      contentAsString(show) must contain(vuRoutes.visibility("vu1").url)
      contentAsString(show) must contain(vuRoutes.search().url)
    }

    "link to holder when a child item" in new ITestApp {
      val show = FakeRequest(vuRoutes.getInVc("vc1,vu1", "vu2"))
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain(vuRoutes.get("vc1").url)
    }

    "allow creating new items with owned descriptions" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("hellokitty"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].identityArea.name" -> Seq("Hello Kitty"),
        "descriptions[0].contentArea.scopeAndContent" -> Seq("Some content"),
        "descriptions[0].identityArea.dates[0].startDate" -> Seq("1939-01-01"),
        "descriptions[0].identityArea.dates[0].endDate" -> Seq("1945-01-01"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = FakeRequest(vuRoutes.createChildPost("vc1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)
      redirectLocation(cr) must beSome(vuRoutes.getInVc("vc1", "hellokitty").url)

      val show = FakeRequest(vuRoutes.getInVc("vc1", "hellokitty"))
        .withUser(privilegedUser).call()
      status(show) must equalTo(OK)

      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("Virtual Collection 1")
      // After having created an item it should contain a 'history' pane
      // on the show page
      contentAsString(show) must contain(vuRoutes.history("hellokitty").url)
      indexEventBuffer.last must equalTo("hellokitty")
    }

    "allow creating and deleting item references" in new ITestApp {
      implicit val apiUser: ApiUser = AuthenticatedUser(privilegedUser.id)
      val origIncludes = await(dataApi.get[VirtualUnit]("vc1")).includedUnits.map(_.id)
      origIncludes must not contain "c1"
      origIncludes must not contain "c4"

      val testData: Map[String, Seq[String]] = Map(
        VirtualUnitF.INCLUDE_REF -> Seq("c1 c4")
      )
      val cr = FakeRequest(vuRoutes.createChildRefPost("vc1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val included = await(dataApi.get[VirtualUnit]("vc1")).includedUnits.map(_.id)
      included must contain("c1")
      included must contain("c4")

      val del = FakeRequest(vuRoutes.deleteChildRefPost("vc1"))
        .withUser(privilegedUser).withCsrf.callWith(Map(
        VirtualUnitF.INCLUDE_REF -> Seq("c1")))
      status(del) must equalTo(SEE_OTHER)

      val newIncludes = await(dataApi.get[VirtualUnit]("vc1")).includedUnits.map(_.id)
      newIncludes must not contain "c1"
      newIncludes must contain("c4")
    }
  }
}
