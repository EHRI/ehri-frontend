package views.export.ead

import java.io.{OutputStreamWriter, StringWriter, ByteArrayOutputStream}

import com.jmcejuela.scala.xml.XMLPrettyPrinter
import play.api.test.PlaySpecification

import scala.xml.XML

/**
 * Formatter should add a doctype and clean-up
 * whitespace. This is surprisingly difficult
 * to get right in Java 6 it seems.
 *
 * @author Mike Bryant (http://github.com/mikesname)
 */
class XmlFormatterSpec extends PlaySpecification {

  val printer = new XMLPrettyPrinter(4)

  private val unformatted =
    """
      |<ead>
      |
      | <eadheader>Test</eadheader>
      | </ead>
    """.stripMargin

  private val formatted =
    """<ead>
      |    <eadheader>Test</eadheader>
      |</ead>
      |""".stripMargin

  """XML formatter""" should {
    "correctly format XML to a string" in {
      printer.format(XML.loadString(unformatted)) must equalTo(formatted)
    }

    "correctly format XML to an output stream" in {
      val writer = new StringWriter()
      printer.write(XML.loadString(unformatted), null, addXmlDeclaration = true)(writer)
      writer.toString must equalTo("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + formatted)
    }
  }
}
