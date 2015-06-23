package views

import javax.inject.{Inject, Provider}

import com.google.inject.Singleton
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.pegdown.ast.{AutoLinkNode, ExpLinkNode}
import org.pegdown.{Extensions, LinkRenderer, PegDownProcessor}


case class PegDownMarkdownRenderer() extends MarkdownRenderer {

  // Pegdown is not thread safe so we need to use a threadlocal
  // for this
  // https://github.com/sirthias/parboiled/issues/11#issuecomment-617256
  private val _pegDown = new ThreadLocal[PegDownProcessor]
  private def getPegDown = {
    Option(_pegDown.get()).getOrElse {
      val parser = new PegDownProcessor(Extensions.AUTOLINKS)
      _pegDown.set(parser)
      parser
    }
  }

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

  override def renderMarkdown(markdown: String): String =
    Jsoup.clean(getPegDown.markdownToHtml(markdown, linkRenderer), whiteListStandard)

  override def renderUntrustedMarkdown(markdown: String): String =
    Jsoup.clean(getPegDown.markdownToHtml(markdown, linkRenderer), whiteListStrict)

  override def renderTrustedMarkdown(markdown: String): String =
    getPegDown.markdownToHtml(markdown, linkRenderer)
}

@Singleton
case class PegDownMarkdownRendererProvider @Inject()() extends Provider[MarkdownRenderer] {
  override lazy val get: MarkdownRenderer = new PegDownMarkdownRenderer
}