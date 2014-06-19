package integration

import helpers._
import models.{GroupF, Group, UserProfileF, UserProfile}
import defines.EntityType
import solr.SolrConstants

/**
 * Spec to test various page views operate as expected.
 */
class SearchSpec extends Neo4jRunnerSpec(classOf[SearchSpec]) {

  import mocks.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )


  "Search views" should {

    "search for hierarchical items with no query should apply a top-level filter" in new FakeApp {
      val search = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.search().url)).get
      status(search) must equalTo(OK)
      mockDispatcher.paramBuffer
        .last.filters.get(SolrConstants.TOP_LEVEL) must equalTo(Some(true))
    }

    "search for hierarchical item with a query should not apply a top-level filter" in new FakeApp {
      val search = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.search().url + "?q=foo")).get
      status(search) must equalTo(OK)
      mockDispatcher.paramBuffer
        .last.filters.get(SolrConstants.TOP_LEVEL) must equalTo(None)
    }

    "handle simple filtering" in new FakeApp {
      val filter = route(fakeLoggedInJsonRequest(privilegedUser, GET,
        controllers.adminutils.routes.AdminSearch.filter().url + "?q=c")).get
      println(contentAsJson(filter))
    }

    "perform indexing correctly" in new FakeApp {

      val cmd: List[String] = List(
        EntityType.DocumentaryUnit.toString,
        EntityType.Repository.toString
      )
      val data = Map[String, Seq[String]](
        "all" -> Seq("true"),
        "type[]" -> cmd
      )

      val idx = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.adminutils.routes.AdminSearch.updateIndexPost().url), data).get
      status(idx) must equalTo(OK)
      // NB: reading the content of the chunked response as a string is
      // necessary to exhaust the iteratee and fill the event buffer.
      contentAsString(idx) must contain("Done")
      mockIndexer.eventBuffer.lastOption must beSome.which { bufcmd =>
        bufcmd must equalTo(cmd.toString())
      }
    }

    "perform hierarchy indexing correctly" in new FakeApp {

      val idx = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
        controllers.archdesc.routes.Repositories.updateIndexPost("r1").url), "").get
      status(idx) must equalTo(OK)
      // NB: reading the content of the chunked response as a string is
      // necessary to exhaust the iteratee and fill the event buffer.
      contentAsString(idx) must contain("Done")
      mockIndexer.eventBuffer.lastOption must beSome.which { bufcmd =>
        bufcmd must equalTo("r1")
      }
    }
  }

  "Search metrics" should {
    "response to JSON" in new FakeApp {
      val repoMetrics = route(fakeLoggedInJsonRequest(privilegedUser, GET,
        controllers.adminutils.routes.Metrics.repositoryCountries().url)).get
      status(repoMetrics) must equalTo(OK)
    }
  }
}
