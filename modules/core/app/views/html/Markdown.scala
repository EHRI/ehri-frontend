package views.html

import play.twirl.api.Html

object Markdown {
  def apply(text: String): Html = Html(views.Helpers.renderMarkdown(text))
}