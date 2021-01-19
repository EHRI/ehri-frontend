package data.markdown

import com.vladsch.flexmark.ast.{LinkNode, Node}
import com.vladsch.flexmark.html.HtmlRenderer.{Builder, HtmlRendererExtension}
import com.vladsch.flexmark.html.renderer.{AttributablePart, NodeRendererContext}
import com.vladsch.flexmark.html.{AttributeProvider, AttributeProviderFactory, HtmlRenderer, IndependentAttributeProviderFactory}
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.profiles.pegdown.{Extensions, PegdownOptionsAdapter}
import com.vladsch.flexmark.util.html.Attributes
import com.vladsch.flexmark.util.options.{DataHolder, MutableDataHolder}


case class FlexmarkMarkdownRenderer() extends RawMarkdownRenderer {
  
  private class LinkAttributeProvider extends AttributeProvider {
    override def setAttributes(node: Node, part: AttributablePart, attributes: Attributes): Unit = {
      node match {
        case a: LinkNode if part == AttributablePart.LINK =>
          attributes.addValue("class", "external")
          attributes.addValue("target", "_blank")
          attributes.addValue("rel", "nofollow noopener")
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

  override def render(markdown: String): String = renderer.render(parser.parse(markdown))
}
