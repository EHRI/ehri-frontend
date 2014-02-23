package models.forms

import play.api.test.PlaySpecification
import models.Address

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class AddressSpec extends PlaySpecification {
  "address form" should {
    "allow relaxed URLs" in {
      import Address.isValidWebsite
      isValidWebsite("www.blah.com") must beTrue
      isValidWebsite("blah.com") must beTrue
      isValidWebsite("http://blah.com") must beTrue
      isValidWebsite("http://www.blah.com") must beTrue
      isValidWebsite("/?helloworld") must beFalse
    }
  }
}
