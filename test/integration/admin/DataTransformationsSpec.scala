package integration.admin

import helpers._
import mockdata.{privilegedUser, unprivilegedUser}
import models.{DataTransformation, FileStage, TransformationType}
import play.api.libs.json.Json
import play.api.test.FakeRequest


class DataTransformationsSpec extends IntegrationTestRunner with ResourceUtils {

  private val dtRoutes = controllers.datasets.routes.DataTransformations
  private val ccRoutes = controllers.datasets.routes.ConvertConfigs

  // NB: reto (unprivilegedUser) can update r2, but not r1, and is not an admin
  private val fixtures = "data-transformation-access-fixtures.sql"

  private def info(name: String) = Json.obj(
    "name" -> name,
    "bodyType" -> TransformationType.XQuery,
    "body" -> "test",
    "comments" -> "test",
    "hasParams" -> false,
  )

  // Statuses of the convert and convertFile endpoints when converting
  // with the given transformation in repository r2, as reto.
  private def convertStatuses(dtId: String)(implicit app: play.api.Application): Seq[Int] = {
    val config = Json.obj("mappings" -> Json.toJson(Seq(dtId -> Json.obj())), "force" -> false)
    Seq(
      FakeRequest(ccRoutes.convert("r2", "default")),
      FakeRequest(ccRoutes.convertFile("r2", "default", FileStage.Input, "test.xml")),
    ).map(r => status(r.withUser(unprivilegedUser).callWith(config)))
  }

  "Data Transformations API" should {

    "list only a repository's own and generic transformations" in new DBTestApp(fixtures) {
      val r = FakeRequest(dtRoutes.list("r2")).withUser(unprivilegedUser).call()
      status(r) must_== OK
      contentAsJson(r).as[Seq[DataTransformation]].map(_.id) must containTheSameElementsAs(
        Seq("r2private1", "generic001"))
    }

    "not allow access to another repository's transformations" in new DBTestApp(fixtures) {
      val get = FakeRequest(dtRoutes.get("r2", "r1private1")).withUser(unprivilegedUser).call()
      status(get) must_== NOT_FOUND
      val update = FakeRequest(dtRoutes.update("r2", "r1private1")).withUser(unprivilegedUser)
        .callWith(info("stolen"))
      status(update) must_== NOT_FOUND
      val delete = FakeRequest(dtRoutes.delete("r2", "r1private1")).withUser(unprivilegedUser).call()
      status(delete) must_== NOT_FOUND

      // Check it is unchanged
      val check = FakeRequest(dtRoutes.get("r1", "r1private1")).withUser(privilegedUser).call()
      contentAsJson(check).as[DataTransformation] must beLike { case dt =>
        dt.name must_== "r1 private"
        dt.repoId must beSome("r1")
      }
    }

    "allow non-admins to use, but not change, generic transformations" in new DBTestApp(fixtures) {
      val get = FakeRequest(dtRoutes.get("r2", "generic001")).withUser(unprivilegedUser).call()
      status(get) must_== OK
      val create = FakeRequest(dtRoutes.create("r2", generic = true)).withUser(unprivilegedUser)
        .callWith(info("new generic"))
      status(create) must_== FORBIDDEN
      val update = FakeRequest(dtRoutes.update("r2", "generic001")).withUser(unprivilegedUser)
        .callWith(info("changed"))
      status(update) must_== FORBIDDEN
      val claim = FakeRequest(dtRoutes.update("r2", "generic001", generic = false)).withUser(unprivilegedUser)
        .callWith(info("claimed"))
      status(claim) must_== FORBIDDEN
      val makeGeneric = FakeRequest(dtRoutes.update("r2", "r2private1", generic = true)).withUser(unprivilegedUser)
        .callWith(info("r2 private"))
      status(makeGeneric) must_== FORBIDDEN
      val delete = FakeRequest(dtRoutes.delete("r2", "generic001")).withUser(unprivilegedUser).call()
      status(delete) must_== FORBIDDEN
    }

    "allow non-admins to manage their repository's transformations" in new DBTestApp(fixtures) {
      val create = FakeRequest(dtRoutes.create("r2")).withUser(unprivilegedUser)
        .callWith(info("new r2"))
      status(create) must_== CREATED
      contentAsJson(create).as[DataTransformation].repoId must beSome("r2")
      val update = FakeRequest(dtRoutes.update("r2", "r2private1")).withUser(unprivilegedUser)
        .callWith(info("r2 changed"))
      status(update) must_== OK
      val delete = FakeRequest(dtRoutes.delete("r2", "r2private1")).withUser(unprivilegedUser).call()
      status(delete) must_== NO_CONTENT
    }

    "allow admins to manage generic transformations" in new DBTestApp(fixtures) {
      val create = FakeRequest(dtRoutes.create("r1", generic = true)).withUser(privilegedUser)
        .callWith(info("new generic"))
      status(create) must_== CREATED
      contentAsJson(create).as[DataTransformation].repoId must beNone
      val update = FakeRequest(dtRoutes.update("r1", "generic001", generic = true)).withUser(privilegedUser)
        .callWith(info("generic changed"))
      status(update) must_== OK
      val delete = FakeRequest(dtRoutes.delete("r1", "generic001")).withUser(privilegedUser).call()
      status(delete) must_== NO_CONTENT
    }

    "not allow another repository's transformations in a conversion config" in new DBTestApp(fixtures) {
      val bad = FakeRequest(ccRoutes.save("r2", "default")).withUser(unprivilegedUser)
        .callWith(Json.toJson(Seq("r1private1" -> Json.obj())))
      status(bad) must_== BAD_REQUEST

      val good = FakeRequest(ccRoutes.save("r2", "default")).withUser(unprivilegedUser)
        .callWith(Json.toJson(Seq("r2private1" -> Json.obj(), "generic001" -> Json.obj())))
      status(good) must_== OK
    }

    "not allow converting with another repository's transformations" in new DBTestApp(fixtures) {
      convertStatuses("r1private1") must_== Seq(BAD_REQUEST, BAD_REQUEST)
    }

    "treat unknown transformations as not found" in new DBTestApp(fixtures) {
      val get = FakeRequest(dtRoutes.get("r2", "missing123")).withUser(unprivilegedUser).call()
      status(get) must_== NOT_FOUND
      val update = FakeRequest(dtRoutes.update("r2", "missing123")).withUser(unprivilegedUser)
        .callWith(info("missing"))
      status(update) must_== NOT_FOUND
      val delete = FakeRequest(dtRoutes.delete("r2", "missing123")).withUser(unprivilegedUser).call()
      status(delete) must_== NOT_FOUND
    }

    "report unknown transformations in conversion configs as bad requests" in new DBTestApp(fixtures) {
      val save = FakeRequest(ccRoutes.save("r2", "default")).withUser(unprivilegedUser)
        .callWith(Json.toJson(Seq("missing123" -> Json.obj())))
      status(save) must_== BAD_REQUEST
      contentAsString(save) must contain("Transformation not found: missing123")

      convertStatuses("missing123") must_== Seq(BAD_REQUEST, BAD_REQUEST)
    }
  }
}
