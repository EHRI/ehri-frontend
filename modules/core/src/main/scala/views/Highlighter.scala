package views

import play.twirl.api.Html

object NoopHighlighter extends Highlighter {
  def highlight(text: String): String = text
  def highlightText(text: String): Html = Html(text)
}

trait Highlighter {
  def highlight(text: String): String
  def highlightText(text: String): Html
}
