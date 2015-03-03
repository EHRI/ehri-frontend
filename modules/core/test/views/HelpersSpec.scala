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
    "sanitise markdown by default" in {
      val md =
        """
          |<script>alert("hello, world")</script>
          |This is some text with a **bold** bit.
        """.stripMargin
      Helpers.renderMarkdown(md) must not contain "<script>"
    }
    "not sanitise trusted markdown" in {
      val md =
        """
          |<script>alert("hello, world")</script>
          |This is some text with a **bold** bit.
        """.stripMargin
      Helpers.renderTrustedMarkdown(md) must contain("""<script>alert("hello, world")</script>""")
    }
    "render auto links with _blank target" in {
      Helpers.renderMarkdown(" an http://www.autolink.com link") must contain("_blank")
    }
    "render auto links with rel=nofollow" in {
      Helpers.renderMarkdown(" an http://www.autolink.com link") must contain("rel=\"nofollow\"")
    }
    "render explicit links with _blank target" in {
      val s: String = Helpers.renderMarkdown(" an [blah](http://www.autolink.com) link")
      s must contain("_blank")
      s must contain("blah")
    }
    "shortens correctly a normal string" in {
    	val nohtml = "This is a test and this is nice because it it not so long and funny"
    	Helpers.ellipsize(nohtml, 50) must have size(50)
    }
    "shortens and remove html tags" in {
    	val html = "<a>This is a test and</a> this is nice because it it not so long and funny"
    	Helpers.ellipsize(html, 50) must not contain("<a>")
    }
  }
}
