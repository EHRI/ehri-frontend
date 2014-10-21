package views

import play.twirl.api.Html

object NoopHighlighter extends Highlighter {
  def highlight(text: String): Html = Html(text)
}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait Highlighter {
  def highlight(text: String): Html
}
