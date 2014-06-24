package views.export.ead

import javax.xml.transform.{OutputKeys, TransformerFactory}
import javax.xml.transform.stream.{StreamSource, StreamResult}
import java.io.{StringReader, StringWriter}

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
object Helpers {
  def tidyXml(xml: String): String = {
    XmlFormatter.format(xml)
  }
}
