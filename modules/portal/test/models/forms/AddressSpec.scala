package models.forms

import play.api.test.PlaySpecification
import models.Address

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class AddressSpec extends PlaySpecification {
  "address form" should {
    "NOT allow relaxed URLs" in {
      import utils.forms.isValidUrl
      isValidUrl("www.blah.com") must beFalse
      isValidUrl("blah.com") must beFalse
      isValidUrl("http://blah.com") must beTrue
      isValidUrl("http://www.blah.com") must beTrue
      isValidUrl("/?helloworld") must beFalse
    }
  }
}
