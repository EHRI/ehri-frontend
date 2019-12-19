package utils.markdown

import java.util

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.node.{Link, Node}
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.{AttributeProvider, AttributeProviderContext, AttributeProviderFactory, HtmlRenderer}

case class CommonmarkMarkdownRenderer() extends RawMarkdownRenderer {

  private class LinkAttributeProvider extends AttributeProvider {
    override def setAttributes(node: Node, tagName: String, attributes: java.util.Map[String,String]): Unit = {
      node match {
        case a: Link =>
          attributes.put("class", "external")
          attributes.put("target", "_blank")
          attributes.put("rel", "nofollow noopener")
        case _ =>
      }
    }
  }

  private val extensions: util.List[Extension] = util.Arrays.asList(AutolinkExtension.create())
  private val parser = Parser.builder().extensions(extensions).build()
  private val renderer = HtmlRenderer.builder()
    .extensions(extensions)
    .attributeProviderFactory(new AttributeProviderFactory {
      override def create(context: AttributeProviderContext): AttributeProvider =
        new LinkAttributeProvider
    })
    .build()

  override def render(markdown: String): String = renderer.render(parser.parse(markdown))
}
