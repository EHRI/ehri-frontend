package data.markdown

import play.api.test.PlaySpecification

class SanitisingMarkdownRendererSpec extends PlaySpecification {
  val mdprocessor = SanitisingMarkdownRenderer(CommonmarkMarkdownRenderer())

  "sanitising markdown renderer" should {
    "parse markdown correctly" in {
      val md =
        """
          |This is some text with a **bold** bit.
        """.stripMargin
      mdprocessor.renderMarkdown(md) must contain("<strong>")
    }
    "sanitise markdown by default" in {
      val md =
        """
          |<script>alert("hello, world")</script>
          |This is some text with a **bold** bit.
        """.stripMargin
      mdprocessor.renderMarkdown(md) must not contain "<script>"
    }
    "not sanitise trusted markdown" in {
      val md =
        """
          |<script>alert("hello, world")</script>
          |This is some text with a **bold** bit.
        """.stripMargin
      mdprocessor.renderTrustedMarkdown(md) must contain( """<script>alert("hello, world")</script>""")
    }
    "render auto links with _blank target" in {
      mdprocessor.renderMarkdown(" an http://www.autolink.com link") must contain("_blank")
    }
    "render auto links with rel=nofollow" in {
      mdprocessor.renderMarkdown(" an http://www.autolink.com link") must contain("rel=\"nofollow\"")
    }
    "render explicit links with _blank target" in {
      val s: String = mdprocessor.renderMarkdown(" an [blah](http://www.autolink.com) link")
      s must contain("_blank")
      s must contain("blah")
    }
  }
}
