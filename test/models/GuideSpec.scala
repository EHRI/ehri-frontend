package models

import helpers.WithSqlFile
import models.sql.IntegrityError
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
      Guide.create(name = "Test", path = "test", virtualUnit = "test", active = 1) must beSuccessfulTry.which { guideOpt =>
        guideOpt must beSome.which { guide =>
          guide.name must equalTo("Test")
        }
      }
      Guide.findAll(activeOnly = true).size must equalTo(3)
      Guide.find("test") must beSome
    }

    "maintain path uniqueness" in new WithSqlFile("guide-fixtures.sql") {
      Guide.findAll(activeOnly = true).size must equalTo(2)
      Guide.create(name = "Test", path = "terezin", virtualUnit = "test", active = 1) must beFailedTry
        .withThrowable[models.sql.IntegrityError]
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

    "update items without changing the path" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val updated = guide.copy(description = Some("blah"))
        updated.update()
        Guide.find("terezin") must beSome.which { foo =>
          foo.description must equalTo(Some("blah"))
        }
      }
    }

    "delete items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.delete()
        Guide.find("terezin") must beNone
        Guide.findAll(activeOnly = false).size must equalTo(1)
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
        val pages: List[GuidePage] = GuidePage.findAll()
        GuidePage.create(
          layout = GuidePage.Layout.Map,
          name = "Test",
          path = "test",
          menu = GuidePage.MenuPosition.Side,
          cypher = "",
          parent = guide.id,
          description = Some("Here is a description"),
          params = None
        ) must beSuccessfulTry.which { opt =>
          GuidePage.findAll().size must equalTo(pages.size + 1)
        }
      }
    }

    "not allow creating items with the same path as other items" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        val pages: List[GuidePage] = GuidePage.findAll()
        GuidePage.create(
          layout = GuidePage.Layout.Map,
          name = "More organisation",
          path = "organisations",
          menu = GuidePage.MenuPosition.Side,
          cypher = "",
          parent = guide.id,
          description = Some("Here is a description"),
          params = None
        ) must beFailedTry.withThrowable[IntegrityError]
      }
    }

    "update items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.findPage("keywords") must beSome.which { page =>
          val updated = page.copy(path = "blah")
          updated.update() must beSuccessfulTry
        }
      }
    }

    "not allow updating items in a way that violates path uniqueness" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.findPage("keywords") must beSome.which { page =>
          val updated = page.copy(path = "organisations")
          updated.update() must beFailedTry.withThrowable[IntegrityError]
        }
      }
    }

    "delete items correctly" in new WithSqlFile("guide-fixtures.sql") {
      Guide.find("terezin") must beSome.which { guide =>
        guide.findPage("keywords") must beSome.which { page =>
          page.delete()
          guide.findPage("keywords") must beNone
        }
      }
    }
  }
}
