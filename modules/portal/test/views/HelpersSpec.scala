package views

import play.api.test.PlaySpecification

class HelpersSpec extends PlaySpecification {
  "view helpers" should {
    "shortens correctly a normal string" in {
    	val nohtml = "This is a test and this is nice because it it not so long and funny"
    	Helpers.ellipsize(nohtml, 50) must have size 50
    }
    "shortens and remove html tags" in {
    	val html = "<a>This is a test and</a> this is nice because it it not so long and funny"
    	Helpers.ellipsize(html, 50) must not contain "<a>"
    }
    "detect RTL text" in {
      val text = "foo"
      Helpers.isRightToLeft(text) must beFalse
      Helpers.isRightToLeft("זה מימין לשמאל") must beTrue
      Helpers.isRightToLeft("אדולף-אברהם ברמן נולד בוורשה בשנת 1906. הוא למד") must beTrue
      Helpers.isRightToLeft("") must beFalse
    }
  }
}
