package data.markdown

/**
  * Markdown renderer which does no sanitisation of HTML output.
  */
trait RawMarkdownRenderer {
  def render(markdown: String): String
}
