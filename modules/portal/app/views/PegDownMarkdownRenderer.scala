package views

import javax.inject.{Inject, Provider, Singleton}

import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import org.parboiled.errors.ParserRuntimeException
import org.pegdown.LinkRenderer.Rendering
import org.pegdown.ast.{AutoLinkNode, ExpLinkNode}
import org.pegdown.{Extensions, LinkRenderer, PegDownProcessor}
import play.api.Logger


case class PegDownMarkdownRenderer() extends MarkdownRenderer {

  private val logger = Logger(getClass)

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
    override def render(node: AutoLinkNode): Rendering = {
      new LinkRenderer.Rendering(node.getText, node.getText)
        .withAttribute("rel", "nofollow")
        .withAttribute("target", "_blank")
        .withAttribute("class", "external")
    }

    override def render(node: ExpLinkNode, text: String): Rendering = {
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

  private def render(markdown: String): String = try {
    getPegDown.markdownToHtml(markdown, linkRenderer)
  } catch {
    case e: ParserRuntimeException =>
      logger.warn(s"Timeout parsing markdown starting: ${markdown.substring(0, 200)}...")
      markdown
  }

  override def renderMarkdown(markdown: String): String =
    Jsoup.clean(render(markdown), whiteListStandard)

  override def renderUntrustedMarkdown(markdown: String): String =
    Jsoup.clean(render(markdown), whiteListStrict)

  override def renderTrustedMarkdown(markdown: String): String =
    render(markdown)
}

@Singleton
case class PegDownMarkdownRendererProvider @Inject()() extends Provider[MarkdownRenderer] {
  override lazy val get: MarkdownRenderer = new PegDownMarkdownRenderer
}