package views.html

import play.twirl.api.Html

object Markdown {
  def apply(text: String)(implicit md: MarkdownRenderer): Html = Html(md.renderMarkdown(text))
}
