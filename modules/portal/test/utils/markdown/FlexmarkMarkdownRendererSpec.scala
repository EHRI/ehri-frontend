package utils.markdown

import play.api.test.PlaySpecification

class FlexmarkMarkdownRendererSpec extends PlaySpecification {
  val mdprocessor = new FlexmarkMarkdownRenderer

  "flexmark markdown renderer" should {
    "parse markdown correctly" in {
      val md =
        """
          |This is some text with a **bold** bit.
        """.stripMargin
      mdprocessor.render(md) must contain("<strong>")
    }
    "render auto links with _blank target" in {
      mdprocessor.render(" an http://www.autolink.com link") must contain("_blank")
    }
    "render auto links with rel=nofollow" in {
      mdprocessor.render(" an http://www.autolink.com link") must contain("rel=\"nofollow noopener\"")
    }
    "render explicit links with _blank target" in {
      val s: String = mdprocessor.render(" an [blah](http://www.autolink.com) link")
      s must contain("_blank")
      s must contain("blah")
    }
  }
}
