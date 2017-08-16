package views

import javax.inject.{Inject, Provider, Singleton}

import com.vladsch.flexmark.ast.{LinkNode, Node}
import com.vladsch.flexmark.html.HtmlRenderer.{Builder, HtmlRendererExtension}
import com.vladsch.flexmark.html.renderer.{AttributablePart, NodeRendererContext}
import com.vladsch.flexmark.html.{AttributeProvider, AttributeProviderFactory, HtmlRenderer, IndependentAttributeProviderFactory}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profiles.pegdown.{Extensions, PegdownOptionsAdapter}
import com.vladsch.flexmark.util.html.Attributes
import com.vladsch.flexmark.util.options.{DataHolder, MutableDataHolder}
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist
import play.api.Logger


case class FlexmarkMarkdownRenderer() extends MarkdownRenderer {
  
  private class LinkAttributeProvider extends AttributeProvider {
    override def setAttributes(node: Node, part: AttributablePart, attributes: Attributes): Unit = {
      node match {
        case a: LinkNode if part == AttributablePart.LINK =>
          attributes.addValue("class", "external")
          attributes.addValue("target", "_blank")
          attributes.addValue("rel", "nofollow")
        case _ =>
      }
    }
  }
  private object LinkAttributeProvider {
    def provider: AttributeProviderFactory = new IndependentAttributeProviderFactory {
      override def create(context: NodeRendererContext): AttributeProvider = new LinkAttributeProvider()
    }
  }

  private val extension: HtmlRendererExtension = new HtmlRenderer.HtmlRendererExtension {
    override def extend(rendererBuilder: Builder, rendererType: String): Unit = {
      rendererBuilder.attributeProviderFactory(LinkAttributeProvider.provider)
    }
    override def rendererOptions(options: MutableDataHolder): Unit = {}
  }

  private val options: DataHolder = PegdownOptionsAdapter.flexmarkOptions(
    Extensions.ALL, extension
  )
  private val parser = Parser.builder(options).build()
  private val renderer = HtmlRenderer.builder(options).build()

  private val whiteListStandard: Whitelist = Whitelist.basic()
    .addAttributes("a", "target", "_blank")
    .addAttributes("a", "class", "external")
    .addAttributes("a", "rel", "nofollow")

  private val whiteListStrict: Whitelist = Whitelist.simpleText()
    .addTags("p", "a")
    .addAttributes("a", "target", "_blank")
    .addAttributes("a", "class", "external")
    .addAttributes("a", "rel", "nofollow")

  private def render(markdown: String): String = renderer.render(parser.parse(markdown))

  override def renderMarkdown(markdown: String): String =
    Jsoup.clean(render(markdown), whiteListStandard)

  override def renderUntrustedMarkdown(markdown: String): String =
    Jsoup.clean(render(markdown), whiteListStrict)

  override def renderTrustedMarkdown(markdown: String): String =
    render(markdown)
}

@Singleton
case class FlexmarkMarkdownRendererProvider @Inject()() extends Provider[MarkdownRenderer] {
  override lazy val get: MarkdownRenderer = new FlexmarkMarkdownRenderer
}