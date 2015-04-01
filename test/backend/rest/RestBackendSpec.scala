package backend.rest

import play.api.test.PlaySpecification
import play.api.libs.json.JsObject
import defines.EntityType
import backend.{AuthenticatedUser, ApiUser, Entity}
import helpers.RestBackendRunner

/**
 * A minimal object that has a resource type and can be read.
 */
case class TestResource(id: String, data: JsObject) extends backend.WithId
object TestResource {
  implicit object Resource extends backend.BackendResource[TestResource] {
    def entityType: EntityType.Value = EntityType.DocumentaryUnit
    import play.api.libs.json._
    import play.api.libs.functional.syntax._
    val restReads: Reads[TestResource] = (
      (__ \ Entity.ID).read[String] and
        (__ \ Entity.DATA).read[JsObject]
      )(TestResource.apply _)
  }
}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class RestBackendSpec extends RestBackendRunner with PlaySpecification {
  sequential

  def backend = RestBackend.withNoopHandler(play.api.Play.current)
  implicit def apiUser: ApiUser = AuthenticatedUser("mike")

  "RestBackend" should {
    "allow fetching objects" in new TestApp {
      val test: TestResource = await(backend.withContext(apiUser).get[TestResource]("c1"))
      test.id must equalTo("c1")
    }
  }
}
