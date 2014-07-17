package views.export.ead

import javax.xml.transform.{OutputKeys, TransformerFactory}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import java.io.{StringReader, StringWriter}
import org.pegdown.{LinkRenderer, Extensions, PegDownProcessor}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Helpers {
  def tidyXml(xml: String): String = XmlFormatter.format(xml)

  // Initialize Markdown processor for rendering markdown. NB: The
  // instance is apparently not thread safe, so using a threadlocal
  // here to be on the safe side.
  private val markdownParser = new ThreadLocal[PegDownProcessor]
  def getMarkdownProcessor = {
    // NB: Eventually we want auto-linking. However this seems
    // to crash pegdown at the moment.
    //import org.pegdown.{Extensions,Parser,PegDownProcessor}
    //val pegdownParser = new Parser(Extensions.AUTOLINKS)
    //new PegDownProcessor//(pegdownParser)
    Option(markdownParser.get).getOrElse {
      val parser = new PegDownProcessor(Extensions.AUTOLINKS)
      markdownParser.set(parser)
      parser
    }
  }

  def renderMarkdownAsEad(text: String): String = new ToEadSerializer(new LinkRenderer)
      .toEad(getMarkdownProcessor.parseMarkdown(
      getMarkdownProcessor.prepareSource(text.toCharArray)))

}
