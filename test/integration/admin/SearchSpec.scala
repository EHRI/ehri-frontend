package integration.admin

import defines.EntityType
import helpers._
import models.{Group, GroupF, UserProfile, UserProfileF}
import play.api.http.MimeTypes
import play.api.test.FakeRequest
import utils.search.SearchConstants

/**
 * Spec to test various page views operate as expected.
 */
class SearchSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )


  "Search views" should {

    "search for hierarchical items with no query should apply a top-level filter" in new ITestApp {
      val search = FakeRequest(controllers.units.routes.DocumentaryUnits.search())
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must equalTo(Some(true))
    }

    "search for hierarchical item with a query should not apply a top-level filter" in new ITestApp {
      val search = FakeRequest(GET, controllers.units.routes.DocumentaryUnits.search().url + "?q=foo")
        .withUser(privilegedUser)
        .call()
      status(search) must equalTo(OK)
      searchParamBuffer
        .last.filters.get(SearchConstants.TOP_LEVEL) must equalTo(None)
    }

    "allow search filtering for non-logged in users" in new ITestApp {
      val filter = FakeRequest(GET, controllers.admin.routes.SearchFilter.filterItems().url + "?q=c")
        .call()
      status(filter) must equalTo(OK)
    }

    "perform indexing correctly" in new ITestApp {
      val cmd: List[String] = List(
        EntityType.DocumentaryUnit.toString,
        EntityType.Repository.toString
      )
      val data = Map[String, Seq[String]](
        "all" -> Seq("true"),
        "type[]" -> cmd
      )

      val idx = FakeRequest(controllers.admin.routes.AdminSearch.updateIndexPost())
          .withUser(privilegedUser)
          .withCsrf
          .callWith(data)
      status(idx) must equalTo(OK)
      // NB: reading the content of the chunked response as a string is
      // necessary to exhaust the iteratee and fill the event buffer.
      contentAsString(idx) must contain("Done")
      indexEventBuffer.lastOption must beSome.which { bufcmd =>
        bufcmd must equalTo(cmd.toString())
      }
    }

    "perform hierarchy indexing correctly" in new ITestApp {
      val idx = FakeRequest(controllers.institutions.routes.Repositories.updateIndexPost("r1"))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(idx) must equalTo(OK)
      // NB: reading the content of the chunked response as a string is
      // necessary to exhaust the iteratee and fill the event buffer.
      contentAsString(idx) must contain("Done")
      indexEventBuffer.lastOption must beSome.which { bufcmd =>
        bufcmd must equalTo("r1")
      }
    }
  }

  "Search metrics" should {
    "response to JSON" in new ITestApp {
      val repoMetrics = FakeRequest(controllers.admin.routes.Metrics.repositoryCountries())
        .withUser(privilegedUser)
        .accepting(MimeTypes.JSON)
        .call()
      status(repoMetrics) must equalTo(OK)
      contentType(repoMetrics) must equalTo(Some(MimeTypes.JSON))
    }
  }
}
