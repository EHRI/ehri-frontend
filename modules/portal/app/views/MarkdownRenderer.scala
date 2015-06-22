package views

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
trait MarkdownRenderer {
  def renderMarkdown(markdown: String): String
  def renderTrustedMarkdown(markdown: String): String
  def renderUntrustedMarkdown(markdown: String): String
}
