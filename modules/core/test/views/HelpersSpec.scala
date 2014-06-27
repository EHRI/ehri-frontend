package views

import play.api.test.PlaySpecification

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class HelpersSpec extends PlaySpecification {
  "view helpers" should {
    "parse markdown correctly" in {
      val md =
        """
          |This is some text with a **bold** bit.
        """.stripMargin
      Helpers.renderMarkdown(md) must contain("<strong>")
    }
    " shortens correctly a normal string " in {
    	val nohtml = "This is a test and this is nice because it it not so long and funny"
    	Helpers.ellipsize(nohtml, 50) must have size(50)
    }
    " shortens and remove html tags" in {
    	val html = "<a>This is a test and</a> this is nice because it it not so long and funny"
    	Helpers.ellipsize(html, 50) must not contain("<a>")
    }
  }
}
