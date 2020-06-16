package integration.admin

import java.io.File

import defines.ContentTypes
import eu.ehri.project.definitions.Entities
import helpers._
import mockdata._
import org.apache.commons.io.FileUtils
import play.api.mvc.Flash
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class ToolsSpec extends IntegrationTestRunner with FakeMultipartUpload {

  "Tools" should {
    "allow uploading moved items" in new ITestApp {
      val f = File.createTempFile("/upload", ".csv")
      f.deleteOnExit()
      FileUtils.writeStringToFile(f, "ιταλία,c1\nfoo,c4", "UTF-8")

      val result = FakeRequest(controllers.admin.routes.Tools.addMovedItemsPost())
        .withFileUpload("csv", f, "text/csv", Map("path-prefix" -> Seq("/units/,/admin/units/")))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must_== OK

      // We've added two items...
      val redirects = movedPages.toList.takeRight(4)
      redirects.headOption must beSome.which { case (from, to) =>
        from must_== "/units/" + java.net.URLEncoder.encode("ιταλία", "UTF-8")
        to must_== "/units/c1"
      }
      redirects.lastOption must beSome.which { case (from, to) =>
        from must_== "/admin/units/foo"
        to must_== "/admin/units/c4"
      }
    }

    "perform ID regeneration" in new ITestApp {
      val result = FakeRequest(controllers.admin.routes.Tools
          .regenerateIdsForType(ContentTypes.DocumentaryUnit))
          .withHeaders("X-REQUESTED-WITH" -> "xmlhttprequest")
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must_== OK
      contentAsString(result) must contain("nl-r1-c1")
      contentAsString(result) must contain("nl-r1-c1-c2")
      contentAsString(result) must contain("nl-r1-c1-c2-c3")
      contentAsString(result) must contain("nl-r1-c4")

      val rename = FakeRequest(controllers.admin.routes.Tools.regenerateIdsPost())
        .withUser(privilegedUser)
        .withCsrf
        .callWith(Map(
          "path-prefix" -> Seq("/units/"),
          "items[0].from" -> Seq("c1"), "items[0].to" -> Seq("nl-r1-c1"), "items[0].active" -> Seq("true"),
          "items[1].from" -> Seq("c2"), "items[1].to" -> Seq("nl-r1-c1-c2"), "items[1].active" -> Seq("true"),
          "items[2].from" -> Seq("c3"), "items[2].to" -> Seq("nl-r1-c1-c2-c3"), "items[2].active" -> Seq("true"),
          "items[3].from" -> Seq("c4"), "items[3].to" -> Seq("nl-r1-c4"), "items[3].active" -> Seq("true")
        ))

      status(rename) must_== OK
      contentAsString(rename) must contain(
        controllers.portal.routes.DocumentaryUnits.browse("nl-r1-c1-c2-c3").url)
    }

    "allow batch renaming items" in new ITestApp {
      val f = File.createTempFile("/upload", ".csv")
      f.deleteOnExit()
      FileUtils.writeStringToFile(f, "c1,new-c1\nc4,new-c4", "UTF-8")

      val result = FakeRequest(controllers.admin.routes.Tools.renameItemsPost())
        .withFileUpload("csv", f, "text/csv", Map("path-prefix" -> Seq("/units/,/admin/units/")))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must_== OK

      // We've added two items...
      val redirects = movedPages.toList.takeRight(4)
      redirects.headOption must beSome.which { case (from, to) =>
        from must_== "/units/c1"
        to must_== "/units/nl-r1-new_c1"
      }
      redirects.lastOption must beSome.which { case (from, to) =>
        from must_== "/admin/units/c4"
        to must_== "/admin/units/nl-r1-new_c4"
      }
      // Clear the mutable buffer to prevent redirects
      // interfering in other tests
      movedPages.clear()
    }

    "allow batch reparenting items" in new ITestApp {
      val f = File.createTempFile("/upload", ".csv")
      f.deleteOnExit()
      FileUtils.writeStringToFile(f, "c4,c1", "UTF-8")

      val result = FakeRequest(controllers.admin.routes.Tools.reparentItemsPost())
        .withFileUpload("csv", f, "text/csv", Map("path-prefix" -> Seq("/units/,/admin/units/")))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must_== OK

      // We've added two items...
      val redirects = movedPages.toList.takeRight(4)
      redirects.headOption must beSome.which { case (from, to) =>
        from must_== "/units/c4"
        to must_== "/units/nl-r1-c1-c4"
      }
      // Clear the mutable buffer to prevent redirects
      // interfering in other tests
      movedPages.clear()
    }

    "handle find/replace correctly" in new ITestApp {
      import models.FindReplaceTask._

      val data: Map[String,Seq[String]] = Map(
        PARENT_TYPE -> Seq(Entities.REPOSITORY),
        SUB_TYPE -> Seq(Entities.REPOSITORY_DESCRIPTION),
        PROPERTY -> Seq(models.Isdiah.AUTHORIZED_FORM_OF_NAME),
        FIND -> Seq("NIOD"),
        REPLACE -> Seq("TEST"),
        LOG_MSG -> Seq("Testing")
      )

      val find = FakeRequest(controllers.admin
        .routes.Tools.findReplacePost())
        .withUser(privilegedUser)
        .withCsrf
        .callWith(data)

      status(find) must_== OK
      contentAsString(find) must contain("NIOD Description")

      val replace = FakeRequest(controllers.admin
        .routes.Tools.findReplacePost(commit = true))
        .withUser(privilegedUser)
        .withCsrf
        .callWith(data)

      status(replace) must_== SEE_OTHER
      flash(replace) must_== Flash(
        Map("success" -> message("admin.utils.findReplace.done", 1)(messagesApi)))
    }


    "handle batch delete correctly" in new ITestApp {
      import models.BatchDeleteTask._

      val data: Map[String,Seq[String]] = Map(
        IDS -> Seq("a1\na2"),
        SCOPE -> Seq("auths"),
        COMMIT -> Seq("true"),
        VERSION -> Seq("true"),
        LOG_MSG -> Seq("Testing")
      )

      val del = FakeRequest(controllers.admin
        .routes.Tools.batchDeletePost())
        .withUser(privilegedUser)
        .withCsrf
        .callWith(data)

      status(del) must_== SEE_OTHER
      flash(del) must_== Flash(
        Map("success" -> message("admin.utils.batchDelete.done", 2)(messagesApi)))
    }

    "allow creating arbitrary redirects" in new ITestApp {
      val from = "/foo"
      val to = controllers.admin.routes.Home.overview().url

      val res = FakeRequest(controllers.admin.routes.Tools.redirectPost())
        .withUser(privilegedUser)
        .withCsrf
        .callWith(Map("from" -> Seq(from), "to" -> Seq(to)))

      status(res) must_== SEE_OTHER
      flash(res) must_== Flash(Map(
        "success" -> message("admin.utils.redirect.done")(messagesApi)))

      val redir = FakeRequest(GET, from).withUser(privilegedUser).call()
      status(redir) must_== MOVED_PERMANENTLY
      redirectLocation(redir) must beSome(to)
    }
  }
}
