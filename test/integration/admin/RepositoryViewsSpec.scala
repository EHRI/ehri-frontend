package integration.admin

import helpers._
import models._
import org.apache.commons.io.FileUtils
import play.api.Application
import play.api.test.FakeRequest
import services.storage.FileStorage

import java.nio.charset.StandardCharsets
import services.data.{DataUser, AuthenticatedUser}


class RepositoryViewsSpec extends IntegrationTestRunner with FakeMultipartUpload {
  import mockdata.{privilegedUser, unprivilegedUser}

  private val repoRoutes = controllers.institutions.routes.Repositories
  private val countryRoutes = controllers.countries.routes.Countries

  // Common headers/strings
  val multipleItemsHeader = "Displaying items"
  val oneItemHeader = "One item found"
  val noItemsHeader = "No items found"

  private def files(implicit app: Application): FileStorage = app.injector.instanceOf[FileStorage]

  private def uploadLogo(id: String, svg: String)(implicit app: Application) = {
    val file = java.io.File.createTempFile("logo", ".svg")
    file.deleteOnExit()
    FileUtils.write(file, svg, StandardCharsets.UTF_8)
    FakeRequest(repoRoutes.updateLogoImagePost(id))
      .withFileUpload("image", file, "image/svg+xml")
      .withUser(privilegedUser)
      .withCsrf
      .call()
  }

  "Repository views" should {

    val COUNTRY = "nl"

    "list should get some items" in new ITestApp {
      val list = FakeRequest(repoRoutes.list()).withUser(unprivilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("r1")
      contentAsString(list) must contain("r2")
    }

    "search should get some items" in new ITestApp {
      val list = FakeRequest(repoRoutes.search()).withUser(unprivilegedUser).call()
      status(list) must equalTo(OK)
      contentAsString(list) must contain(multipleItemsHeader)
      contentAsString(list) must contain("r1")
      contentAsString(list) must contain("r2")
    }

    "show correct default values in the form when creating new items" in new ITestApp(
      Map("formConfig.Repository.holdings.default" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(countryRoutes.createRepository(COUNTRY)).withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must contain("SOME RANDOM VALUE")
    }

    "NOT show default values in the form when editing items" in new ITestApp(
      Map("formConfig.Repository.holdings.default" -> "SOME RANDOM VALUE")) {
      val form = FakeRequest(repoRoutes.update("r1")).withUser(privilegedUser).call()
      status(form) must equalTo(OK)
      contentAsString(form) must not contain "SOME RANDOM VALUE"
    }


    "allow creating new items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("wiener-library"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("Wiener Library"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Wiener Library (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("Some history"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("Some content"),
        "descriptions[0].addressArea[0].addressName" -> Seq("An Address"),
        "descriptions[0].addressArea[0].email[0]" -> Seq("foo@example.com"),
        "descriptions[0].addressArea[0].telephone[0]" -> Seq("12345 546395"),
        "descriptions[0].controlArea[0].sources[0]" -> Seq("ClaimsCon"),
        "descriptions[0].controlArea[0].sources[1]" -> Seq("YV"),
        "publicationStatus" -> Seq("Published")
      )
      val cr = FakeRequest(countryRoutes.createRepositoryPost(COUNTRY))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("Some history")
      contentAsString(show) must contain("Some content")
      contentAsString(show) must contain("An Address")
      contentAsString(show) must contain("12345 546395")
      indexEventBuffer.last must equalTo("nl-wiener_library")
    }

    "error if missing mandatory values" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
      )
      val cr = FakeRequest(countryRoutes.createRepositoryPost(COUNTRY))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(BAD_REQUEST)
    }

    "give a form error when creating items with an existing identifier" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1")
      )
      val cr = FakeRequest(countryRoutes.createRepositoryPost(COUNTRY))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)
      val cr2 = FakeRequest(countryRoutes.createRepositoryPost(COUNTRY))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr2) must equalTo(BAD_REQUEST)
    }


    "link to other privileged actions when logged in" in new ITestApp {
      val show = FakeRequest(repoRoutes.get("r1")).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain(repoRoutes.update("r1").url)
      contentAsString(show) must contain(repoRoutes.delete("r1").url)
      contentAsString(show) must contain(repoRoutes.createDoc("r1").url)
      contentAsString(show) must contain(repoRoutes.visibility("r1").url)
      contentAsString(show) must contain(repoRoutes.search().url)
    }

    "allow updating items when logged in as privileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("Repository 1"),
        "descriptions[0].otherFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
        "descriptions[0].parallelFormsOfName[0]" -> Seq("Repository 1 (Alt)"),
        "descriptions[0].descriptionArea.history" -> Seq("New History for r1"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("New Content for r1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = FakeRequest(repoRoutes.updatePost("r1"))
        .withUser(privilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(SEE_OTHER)

      val show = FakeRequest(GET, redirectLocation(cr).get).withUser(privilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must contain("New Content for r1")
      indexEventBuffer.last must equalTo("r1")
    }

    "disallow updating items when logged in as unprivileged user" in new ITestApp {
      val testData: Map[String, Seq[String]] = Map(
        "identifier" -> Seq("r1"),
        "descriptions[0].languageCode" -> Seq("eng"),
        "descriptions[0].name" -> Seq("Repository 1"),
        "descriptions[0].descriptionArea.geoculturalContext" -> Seq("New Content for r1"),
        "publicationStatus" -> Seq("Draft")
      )
      val cr = FakeRequest(repoRoutes.updatePost("r1"))
        .withUser(unprivilegedUser).withCsrf.callWith(testData)
      status(cr) must equalTo(FORBIDDEN)

      // We can view the item when not logged in...
      val show = FakeRequest(repoRoutes.get("r1")).withUser(unprivilegedUser).call()
      status(show) must equalTo(OK)
      contentAsString(show) must not contain "New Content for r1"
    }

    "not change items when submitting an unedited form" in new ITestApp {
      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      val r1 = await(dataApi.get[Repository]("r1"))
      val form = FakeRequest(repoRoutes.update("r1")).withUser(privilegedUser).call()
      val data = formData(contentAsString(form))
      val cr = FakeRequest(repoRoutes.updatePost("r1"))
        .withUser(privilegedUser)
        .withCsrf
        .callWith(data)
      status(cr) must equalTo(SEE_OTHER)
      val r2 = await(dataApi.get[Repository]("r1"))
      r2.data must_== r1.data
    }

    "allow deleting all items in a repository" in new ITestApp {
      val formData: Map[String, Seq[String]] = Map(
        "all" -> Seq("true"),
        "confirm" -> Seq("delete 5 items"),
        "answer" -> Seq("delete 5 items")
      )
      val cr = FakeRequest(repoRoutes.deleteContentsPost("r1"))
        .withUser(privilegedUser).withCsrf.callWith(formData)
      status(cr) must_== SEE_OTHER
      // we should have 5 delete events for the items, plus an update event for the repo...
      indexEventBuffer.size must beGreaterThan(5)
      indexEventBuffer.takeRight(6).sorted must_== Seq("c1", "c2", "c3", "c4", "nl-r1-m19", "r1")
    }

    "allow uploading an SVG logo, unchanged" in new ITestApp {
      val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="10" height="10"><rect width="10" height="10"/></svg>"""
      val result = uploadLogo("r1", svg)
      status(result) must equalTo(SEE_OTHER)

      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      val logoUrl = await(dataApi.get[Repository]("r1")).data.logoUrl
      logoUrl must beSome(endWith(".svg"))
      val path = s"$hostInstance/images/Repository/" + logoUrl.get.split("/").last
      await(files.get(path)) must beSome.like { case (meta, _) =>
        meta.contentType must beSome("image/svg+xml")
      }
    }

    "allow removing a logo" in new ITestApp {
      await(uploadLogo("r1", """<svg xmlns="http://www.w3.org/2000/svg"/>"""))
      implicit val apiUser: DataUser = AuthenticatedUser(privilegedUser.id)
      await(dataApi.get[Repository]("r1")).data.logoUrl must beSome

      // the logo page offers to remove it
      val page = FakeRequest(repoRoutes.updateLogoImage("r1")).withUser(privilegedUser).call()
      contentAsString(page) must contain(repoRoutes.deleteLogoImagePost("r1").url)

      val result = FakeRequest(repoRoutes.deleteLogoImagePost("r1"))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must equalTo(SEE_OTHER)
      await(dataApi.get[Repository]("r1")).data.logoUrl must beNone
    }
  }
}
