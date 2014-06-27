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
  }
}
