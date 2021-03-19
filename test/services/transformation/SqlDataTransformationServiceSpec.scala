package services.transformation

import helpers._
import models.{DataTransformation, DataTransformationInfo}
import play.api.Application


class SqlDataTransformationServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlDataTransformationService]

  private val dsId = "default";
  private val dtId = "FG8jdRd43j" // from fixture file

  "Data transformation service" should {
    "list items" in new DBTestApp("data-transformation-fixtures.sql") {
      val dts = await(service.list())
      dts.size must_== 1
    }

    "locate items" in new DBTestApp("data-transformation-fixtures.sql") {
      val dt = await(service.get(dtId))
      dt.name must_== "test"
    }

    "delete items" in new DBTestApp("data-transformation-fixtures.sql") {
      await(service.delete(dtId)) must_== true
    }

    "create items" in new DBTestApp("data-transformation-fixtures.sql") {
      val dt = await(service.create(DataTransformationInfo("test2", DataTransformation.TransformationType.Xslt, "foo", "comment"), Some("r2")))
      dt.name must_== "test2"
    }

    "create items enforcing unique names" in new DBTestApp("data-transformation-fixtures.sql") {
      val dt = await(service.create(DataTransformationInfo("test2", DataTransformation.TransformationType.Xslt, "foo", "comment"), Some("r2")))
      dt.name must_== "test2"

      await(service.create(DataTransformationInfo("test2",
        DataTransformation.TransformationType.Xslt, "foo", "comment"), Some("r2"))) must throwA[DataTransformationExists]
    }

    "update items" in new DBTestApp("data-transformation-fixtures.sql") {
      val dt = await(service.get(dtId))
      val dt2 = await(service.update(dt.id, DataTransformationInfo("blah", dt.bodyType, dt.body, dt.comments), Some("r2")))
      dt2.name must_== "blah"
    }

    "save repository configs" in new DBTestApp("data-transformation-fixtures.sql") {
      val saved = await(service.saveConfig("r2", dsId, Seq(dtId)))
      saved must_== 1

      val saved2 = await(service.saveConfig("r2", dsId, Seq.empty))
      saved2 must_== 0

      val saved3 = await(service.saveConfig("r2", dsId, Seq(dtId)))
      saved3 must_== 1

      val dt = await(service.create(DataTransformationInfo("test2", DataTransformation.TransformationType.Xslt, "foo", "comment"), Some("r2")))
      val saved4 = await(service.saveConfig(repoId = "r2", dsId, Seq(dtId, dt.id)))
      saved4 must_== 2

      val saved5 = await(service.saveConfig("r2", dsId, Seq.empty))
      saved5 must_== 0
    }

    "get repository configs" in new DBTestApp("data-transformation-fixtures.sql") {
      val dt = await(service.create(DataTransformationInfo("test2", DataTransformation.TransformationType.Xslt, "foo", "comment"), Some("r2")))
      await(service.saveConfig("r2", dsId, Seq(dt.id, dtId)))
      val configs = await(service.getConfig("r2", dsId))
      configs.size must_== 2
      configs.head.name must_== "test2"
    }
  }
}
