package utils

import play.api.test.{FakeRequest, PlaySpecification}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class DateFacetUtilsSpec extends PlaySpecification {
  import DateFacetUtils._

  "date utils" should {
    "format correctly as Solr" in {
      formatAsSolrQuery("1940-1980") must equalTo("[1940-01-01T00:00:00.000Z TO 1980-12-12T23:59:00.000Z]")
      formatAsSolrQuery("1940-") must equalTo("[1940-01-01T00:00:00.000Z TO *]")
      formatAsSolrQuery("1940-1940") must equalTo("[1940-01-01T00:00:00.000Z TO 1940-12-12T22:59:00.000Z]")
      formatAsSolrQuery("-1980") must equalTo("[* TO 1980-12-12T23:59:00.000Z]")
      // Parse invalid ranges sensibly
      formatAsSolrQuery("1980-1940") must equalTo("[1940-01-01T00:00:00.000Z TO 1980-12-12T23:59:00.000Z]")
    }

    "correctly convert to readable form" in {
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
