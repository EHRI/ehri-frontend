package models

import helpers.WithSqlFile
import play.api.test.PlaySpecification


class GuideSpec extends PlaySpecification {

  "Guide model" should {
    "locate items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.findAll(activeOnly = true).size must equalTo(1)

      Guide.find("terezin") must beSome.which { guide =>
        guide.name must equalTo("Terezin")
        guide.getPages.size must beGreaterThan(0)
        guide.getPage("people") must beSome.which { page =>
          page.name must equalTo("People")
        }
      }
    }

    "create items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.findAll(activeOnly = true).size must equalTo(1)
      Guide.create(name = "Test", path = "test", active = true) must beSome
      Guide.findAll(activeOnly = true).size must equalTo(2)
      Guide.find("test") must beSome
    }

    "update items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val updated = guide.copy(path = "foo")
        updated.update()
        Guide.find("foo") must beSome.which { foo =>
          foo.name must equalTo("Terezin")
        }
      }
    }

    "delete items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.delete()
        Guide.find("terezin") must beNone
      }
    }
  }

  "GuidePage model" should {
    "locate items correctly" in new WithSqlFile("guide-fixtures.sql") {
      val pages: List[GuidePage] = GuidePage.findAll()
      pages.size must beGreaterThan(0)
      GuidePage.find("people") must be
    }

    "create items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        GuidePage.create(
          layout = GuidePage.Layout.Map,
          name = "Test",
          path = "test",
          menu = GuidePage.MenuPosition.Side,
          cypher = "",
          parent = guide.objectId
        ) must beSome
      }
    }

    "update items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val updated = guide.copy(path = "foo")
        updated.update()
        Guide.find("foo") must beSome.which { foo =>
          foo.name must equalTo("Terezin")
        }
      }
    }

    "delete items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.delete()
        Guide.find("terezin") must beNone
      }
    }
  }
}
