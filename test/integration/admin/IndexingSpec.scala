package integration.admin

import defines.EntityType
import helpers._
import models.{Group, GroupF, UserProfile, UserProfileF}
import play.api.http.MimeTypes
import play.api.libs.json.Json
import play.api.test.FakeRequest
import utils.search.SearchConstants

/**
 * Spec to test various page views operate as expected.
 */
class IndexingSpec extends IntegrationTestRunner {

  import mockdata.privilegedUser

  val userProfile = UserProfile(
    model = UserProfileF(id = Some(privilegedUser.id), identifier = "test", name="test user"),
    groups = List(Group(GroupF(id = Some("admin"), identifier = "admin", name="Administrators")))
  )


  "Indexing views" should {

    "perform indexing correctly" in new ITestApp {
      val cmd: List[String] = List(
        EntityType.DocumentaryUnit.toString,
        EntityType.Repository.toString
      )
      val data = Json.obj(
        "deleteAll" -> false,
        "deleteTypes" -> false,
        "types" -> cmd
      )

      // FIXME! find out how to test websockets...
      skipped("work out how to test websockets")
    }

    "perform hierarchy indexing correctly" in new ITestApp {
      // FIXME! find out how to test websockets...
      skipped("work out how to test websockets")
    }
  }
}
