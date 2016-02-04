package views

import play.twirl.api.Html

object NoopHighlighter extends Highlighter {
  def highlight(text: String): String = text
}

trait Highlighter {
  def highlight(text: String): String
}
