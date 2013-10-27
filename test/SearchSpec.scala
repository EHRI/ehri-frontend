package test

import helpers._
import models.{GroupF, Group, UserProfileF, UserProfile}
import defines.EntityType
import play.api.test._
import play.api.test.Helpers._
import play.api.mvc.{AsyncResult, ChunkedResult}
import scala.concurrent.Future
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
        controllers.archdesc.routes.DocumentaryUnits.search.url)).get
      status(search) must equalTo(OK)
      mockDispatcher.paramBuffer
        .last.filters.get(SolrConstants.TOP_LEVEL) must equalTo(Some(true))
    }

    "search for hierarchical item with a query should not apply a top-level filter" in new FakeApp {
      val search = route(fakeLoggedInHtmlRequest(privilegedUser, GET,
        controllers.archdesc.routes.DocumentaryUnits.search.url + "?q=foo")).get
      status(search) must equalTo(OK)
      mockDispatcher.paramBuffer
        .last.filters.get(SolrConstants.TOP_LEVEL) must equalTo(None)
    }

    "perform indexing correctly" in new FakeApp {

      val data = Map[String,Seq[String]](
        "all" -> Seq("true"),
        "type[]" -> Seq(
          EntityType.DocumentaryUnit.toString,
          EntityType.Repository.toString
        )
      )

      val idx = route(fakeLoggedInHtmlRequest(privilegedUser, POST,
          controllers.admin.routes.Search.updateIndexPost.url), data).get
      status(idx) must equalTo(OK)
      println(mockIndexer.eventBuffer)
    }
  }

  "Search metrics" should {
    "response to JSON" in new FakeApp {
      val repoMetrics = route(fakeLoggedInJsonRequest(privilegedUser, GET,
        controllers.admin.routes.Metrics.repositoryCountries.url)).get
      status(repoMetrics) must equalTo(OK)
    }
  }
}
