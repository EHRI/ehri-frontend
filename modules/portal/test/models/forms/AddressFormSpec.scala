package models.forms

import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class AddressFormSpec extends PlaySpecification {
  "address form" should {
    "allow relaxed URLs" in {
      import AddressForm.isValidWebsite
      isValidWebsite("www.blah.com") must beTrue
      isValidWebsite("blah.com") must beTrue
      isValidWebsite("http://blah.com") must beTrue
      isValidWebsite("http://www.blah.com") must beTrue
      isValidWebsite("/?helloworld") must beFalse
    }
  }
}
