package views

import javax.inject.{Inject, Provider}

import com.google.inject.Singleton
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.pegdown.ast.{AutoLinkNode, ExpLinkNode}
import org.pegdown.{Extensions, LinkRenderer, PegDownProcessor}

/**
 * Provider for the Pegdown Markdown renderer component.
 *
 * The pegdown processor is expensive to create, so only do it once.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
@Singleton
case class PegdownMarkdownRendererProvider @Inject()() extends Provider[MarkdownRenderer] {
  override lazy val get: MarkdownRenderer = new MarkdownRenderer {
    private val linkRenderer = new LinkRenderer() {
      override def render(node: AutoLinkNode) = {
        new LinkRenderer.Rendering(node.getText, node.getText)
          .withAttribute("rel", "nofollow")
          .withAttribute("target", "_blank")
          .withAttribute("class", "external")
      }

      override def render(node: ExpLinkNode, text: String) = {
        new LinkRenderer.Rendering(node.url, text)
          .withAttribute("rel", "nofollow")
          .withAttribute("target", "_blank")
          .withAttribute("class", "external")
          .withAttribute("title", node.title)
      }
    }
    private val whiteListStandard: Whitelist = Whitelist.basic()
      .addAttributes("a", "target", "_blank")
      .addAttributes("a", "class", "external")
      .addAttributes("a", "rel", "nofollow")

    private val whiteListStrict: Whitelist = Whitelist.simpleText().addTags("p", "a")
      .addAttributes("a", "target", "_blank")
      .addAttributes("a", "class", "external")
      .addAttributes("a", "rel", "nofollow")

    private val delegate = new PegDownProcessor(Extensions.AUTOLINKS)

    override def renderMarkdown(markdown: String): String =
      Jsoup.clean(delegate.markdownToHtml(markdown, linkRenderer), whiteListStandard)

    override def renderUntrustedMarkdown(markdown: String): String =
      Jsoup.clean(delegate.markdownToHtml(markdown, linkRenderer), whiteListStrict)

    override def renderTrustedMarkdown(markdown: String): String =
      delegate.markdownToHtml(markdown, linkRenderer)
  }
}
