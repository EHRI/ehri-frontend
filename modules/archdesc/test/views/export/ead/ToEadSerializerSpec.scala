package views.export.ead

import play.api.test.PlaySpecification
import org.pegdown.{LinkRenderer, PegDownProcessor}
import org.pegdown.ast.RootNode

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class ToEadSerializerSpec extends PlaySpecification {

  val testMd =
    """
      |# This is a title
      |
      |Some text.
      |With a line-break indicated by two spaces at the end
      |of the previous line.
      |
      |Hello, world. This is some markdown. Here is a list:
      |
      | * one item
      | * two item
      | * three item
      |
      |Here is an ordered list:
      |
      | 1. one item
      | 2. two item
      | 3. three item
      |
      |Blah blah and [a link](http://portal.ehri-project.eu).
    """.stripMargin

  "ToEadSerializer" should {
    "convert markdown to EAD XML correctly" in {
      val processor = new PegDownProcessor
      val markdown: RootNode = processor.parseMarkdown(processor
        .prepareSource(testMd.toCharArray))
      val eadSerializer = new ToEadSerializer(new LinkRenderer)
      val ead: String = eadSerializer.toEad(markdown)
      ead must contain("<head>")
      ead must contain("<list>")
      ead must contain("<item>")
      ead must contain("extptr")
    }
  }
}
