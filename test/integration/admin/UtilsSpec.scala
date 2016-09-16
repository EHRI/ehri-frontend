package integration.admin

import java.io.File

import helpers._
import mockdata._
import org.apache.commons.io.FileUtils
import play.api.test.FakeRequest

/**
 * Spec to test various page views operate as expected.
 */
class UtilsSpec extends IntegrationTestRunner with FakeMultipartUpload {

  "Utils" should {
    "return a successful ping of the EHRI REST service and search engine" in new ITestApp {
      val ping = FakeRequest(controllers.admin.routes.Utils.checkServices()).call()
      status(ping) must equalTo(OK)
      contentAsString(ping) must_== "ehri\tok\nsolr\tok"
    }

    "check user sync correctly" in new ITestApp {
      val check = FakeRequest(controllers.admin.routes.Utils.checkUserSync()).call()
      status(check) must equalTo(OK)
      // User joeblogs exists in the account mocks but not the
      // graph DB fixtures, so the sync check should (correcly)
      // highlight this.
      contentAsString(check) must contain("joeblogs")
    }

    "allow uploading moved items" in new ITestApp {

      val f = File.createTempFile("/upload", ".csv")
      f.deleteOnExit()
      FileUtils.writeStringToFile(f, "ιταλία\tc1\nfoo\tc4", "UTF-8")

      val result = FakeRequest(controllers.admin.routes.Utils.addMovedItemsPost())
        .withFileUpload("tsv", f, "text/tsv", Map("path-prefix" -> Seq("/units/")))
        .withUser(privilegedUser)
        .withCsrf
        .call()
      status(result) must_== OK

      // We've added two items...
      val redirects = movedPages.toList.takeRight(2)
      redirects.headOption must beSome.which { case (from, to) =>
        from must_== "/units/" + java.net.URLEncoder.encode("ιταλία", "UTF-8")
        to must_== "/units/c1"
      }
      redirects.lastOption must beSome.which { case (from, to) =>
        from must_== "/units/foo"
        to must_== "/units/c4"
      }
    }
  }
}
