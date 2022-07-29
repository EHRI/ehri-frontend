package models

import play.api.libs.json.Json
import play.api.test.PlaySpecification

class OaiPmhConfigSpec extends PlaySpecification {
  "OaiPmhConfig" should {
    "serialize correctly from JSON" in {
      val json = Json.parse(
        """{
            "url": "https://foo.bar/baz",
            "format": "oai_dc",
            "set": "test",
            "from": "2020-01-01T00:00:00Z",
            "until": null
           }
        """)
      json.validate[OaiPmhConfig].asOpt must beSome.which { config =>
        config.from must beSome
        config.until must beNone
      }
    }
  }
}
