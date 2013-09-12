package test

import helpers._
import models.{GroupF, Group, UserProfileF, UserProfile}
import defines.EntityType
import play.api.test._
import play.api.test.Helpers._

/**
 * Spec to test various page views operate as expected.
 */
class SearchSpec extends Neo4jRunnerSpec(classOf[SearchSpec]) {

  import mocks.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.profile_id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )


  "Search views" should {

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

  step {
    runner.stop()
  }
}
