package utils

import play.api.test.{FakeRequest, PlaySpecification}
import utils.search.{End, Start, Val}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class DateFacetUtilsSpec extends PlaySpecification {
  import utils.DateFacetUtils._

  "date utils" should {
    "format correctly as Solr" in {
      formatAsQuery("1940-1980") must equalTo(Val("1940-01-01T00:00:00.000Z") to Val("1980-12-12T23:59:00.000Z"))
      formatAsQuery("1940-") must equalTo(Val("1940-01-01T00:00:00.000Z") to End)
      formatAsQuery("1940-1940") must equalTo(Val("1940-01-01T00:00:00.000Z") to Val("1940-12-12T22:59:00.000Z"))
      formatAsQuery("-1980") must equalTo(Start to Val("1980-12-12T23:59:00.000Z"))
      // Parse invalid ranges sensibly - ranges with
      // end less than start should be swapped.
      formatAsQuery("1980-1940") must equalTo(Val("1940-01-01T00:00:00.000Z") to Val("1980-12-12T23:59:00.000Z"))
    }

    "correctly convert to readable form" in {
      // NB: We need a request in scope for i18n, but since there's
      // no application (and therefore messages files) we don't
      // actually get a language-aware string out
      implicit val fakeReq = FakeRequest()
      formatReadable("1940-1980") must equalTo(Some(DATE_PARAM + ".between"))
      formatReadable("1980-1940") must equalTo(Some(DATE_PARAM + ".between"))
      formatReadable("1940-") must equalTo(Some(DATE_PARAM + ".after"))
      formatReadable("-1980") must equalTo(Some(DATE_PARAM + ".before"))
      formatReadable("1940-1940") must equalTo(Some(DATE_PARAM + ".exact"))
      formatReadable("-") must equalTo(Some(DATE_PARAM + ".all"))
    }
  }
}
