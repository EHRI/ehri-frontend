package models

import helpers.WithSqlFile
import play.api.test.PlaySpecification


class GuideSpec extends PlaySpecification {

  val terezinGuideName = "Terezín Research Guide" // note diacritic i!

  "Guide model" should {
    "locate items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.findAll(activeOnly = true).size must equalTo(2)

      Guide.find("terezin") must beSome.which { guide =>
        guide.name must equalTo(terezinGuideName)
        guide.findPages().size must beGreaterThan(0)
        guide.findPage("people") must beSome.which { page =>
          page.name must equalTo("People")
        }
      }
    }

    "create items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.findAll(activeOnly = true).size must equalTo(2)
      Guide.create(name = "Test", path = "test", virtualUnit = "test", active = 1) must beSome
      Guide.findAll(activeOnly = true).size must equalTo(3)
      Guide.find("test") must beSome
    }

    "update items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val updated = guide.copy(path = "foo")
        updated.update()
        Guide.find("foo") must beSome.which { foo =>
          foo.name must equalTo(terezinGuideName)
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
          parent = guide.id,
          params = None
        ) must beSome
      }
    }

    "update items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val updated = guide.copy(path = "foo")
        updated.update()
        Guide.find("foo") must beSome.which { foo =>
          foo.name must equalTo(terezinGuideName)
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
