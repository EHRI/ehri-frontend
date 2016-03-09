package models

import models.sql.IntegrityError
import play.api.test.PlaySpecification

import helpers.withDatabaseFixture

class GuideSpec extends PlaySpecification {

  val terezinGuideName = "Terezín Research Guide" // note diacritic i!

  "Guide model" should {
    "locate items correctly" in  {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          guide.name must equalTo(terezinGuideName)
          dao.findPages(guide).size must beGreaterThan(0)
          dao.findPage(guide, "people") must beSome.which { page =>
            page.name must equalTo("People")
          }
        }
      }
    }

    "create items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.findAll(activeOnly = true).size must equalTo(2)
        dao.create(name = "Test", path = "test", virtualUnit = "test", active = true) must beSuccessfulTry.which { guideOpt =>
          guideOpt must beSome.which { guide =>
            guide.name must equalTo("Test")
          }
        }
        dao.findAll(activeOnly = true).size must equalTo(3)
        dao.find("test") must beSome
      }
    }

    "maintain path uniqueness" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.findAll(activeOnly = true).size must equalTo(2)
        dao.create(name = "Test", path = "terezin", virtualUnit = "test", active = true) must beFailedTry
          .withThrowable[models.sql.IntegrityError]
      }
    }

    "update items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          val updated = guide.copy(path = "foo")
          dao.update(updated)
          dao.find("foo") must beSome.which { foo =>
            foo.name must equalTo(terezinGuideName)
          }
        }
      }
    }

    "update items without changing the path" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          val updated = guide.copy(description = Some("blah"))
          dao.update(updated)
          dao.find("terezin") must beSome.which { foo =>
            foo.description must equalTo(Some("blah"))
          }
        }
      }
    }

    "delete items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          dao.delete(guide)
          dao.find("terezin") must beNone
          dao.findAll(activeOnly = false).size must equalTo(1)
        }
      }
    }
  }

  "GuidePage model" should {
    "locate items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        val pages: List[GuidePage] = dao.findAllPages()
        pages.size must beGreaterThan(0)
        dao.findPage("people") must be
      }
    }

    "create items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          val pages: List[GuidePage] = dao.findAllPages()
          dao.createPage(
            layout = GuidePage.Layout.Map,
            name = "Test",
            path = "test",
            menu = GuidePage.MenuPosition.Side,
            cypher = "",
            parent = guide.id,
            description = Some("Here is a description"),
            params = None
          ) must beSuccessfulTry.which { opt =>
            dao.findAllPages().size must equalTo(pages.size + 1)
          }
        }
      }
    }

    "not allow creating items with the same path as other items" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()

        dao.find("terezin") must beSome.which { guide =>
          val pages: List[GuidePage] = dao.findAllPages()
          dao.createPage(
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
    }

    "update items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          dao.findPage(guide, "keywords") must beSome.which { page =>
            val updated = page.copy(path = "blah")
            dao.updatePage(updated) must beSuccessfulTry
          }
        }
      }
    }

    "not allow updating items in a way that violates path uniqueness" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          dao.findPage(guide, "keywords") must beSome.which { page =>
            val updated = page.copy(path = "organisations")
            dao.updatePage(updated) must beFailedTry.withThrowable[IntegrityError]
          }
        }
      }
    }

    "delete items correctly" in {
      withDatabaseFixture("guide-fixtures.sql") { implicit db =>
        val dao = SqlGuideService()
        dao.find("terezin") must beSome.which { guide =>
          dao.findPage(guide, "keywords") must beSome.which { page =>
            dao.deletePage(page)
            dao.findPage(guide, "keywords") must beNone
          }
        }
      }
    }
  }
}
