package utils.http

import play.api.test.PlaySpecification

class HttpSpec extends PlaySpecification {
  "HTTP helpers" should {
    "convert relative IRIs to URIs correctly" in {
      val iri = "/test/בדיקה"
      iriToUri(iri) must_== "/test/%D7%91%D7%93%D7%99%D7%A7%D7%94"
    }
    "convert absolute IRIs to URIs correctly" in {
      val iri = "https://example.com/test/בדיקה"
      iriToUri(iri) must_== "https://example.com/test/%D7%91%D7%93%D7%99%D7%A7%D7%94"
    }
  }
}
