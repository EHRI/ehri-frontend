package services.harvesting

import helpers._
import models.UrlSetConfig
import play.api.Application

class SqlUrlSetConfigServiceSpec extends IntegrationTestRunner {

  def service(implicit app: Application) = app.injector.instanceOf[SqlUrlSetConfigService]

  "UrlSet config service" should {
    "locate items" in new DBTestApp("import-url-set-config-fixtures.sql") {
      val config = await(service.get("r1", "2"))
      config must beSome.which { (c: UrlSetConfig) =>
        c.urlMap must_== Seq("http://example.com" -> "test")
        c.headers must beSome(Seq("Accept" -> "text/xml"))
      }
    }

    "delete items" in new DBTestApp("import-url-set-config-fixtures.sql") {
      await(service.delete("r1", "1")) must_== true
    }

    "create items" in new DBTestApp("import-url-set-config-fixtures.sql") {
      val config = UrlSetConfig(Seq("http://example.com/bar" -> "file1"), headers = Some(Seq("Accept" -> "text/xml")))
      await(service.save("r1", "3", config)) must_== config
    }

    "update items" in new DBTestApp("import-url-set-config-fixtures.sql") {
      val configs1 = UrlSetConfig(Seq("http://example.com/bar" -> "file1"), headers = Some(Seq("Accept" -> "application/json")))
      await(service.save("r1", "1", configs1)) must_== configs1

      val configs2 = UrlSetConfig(Seq("http://example.com/bar" -> "file1"), headers = None)
      await(service.save("r1", "1", configs2)) must_== configs2
    }
  }
}
