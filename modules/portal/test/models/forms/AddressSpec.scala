package models.forms

import play.api.test.PlaySpecification

class AddressSpec extends PlaySpecification {
  "address form" should {
    "NOT allow relaxed URLs" in {
      forms.isValidUrl("www.blah.com") must beFalse
      forms.isValidUrl("blah.com") must beFalse
      forms.isValidUrl("http://blah.com") must beTrue
      forms.isValidUrl("http://www.blah.com") must beTrue
      forms.isValidUrl("/?helloworld") must beFalse
    }
  }
}
