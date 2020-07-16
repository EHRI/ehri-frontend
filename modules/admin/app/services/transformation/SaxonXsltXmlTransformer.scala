package services.transformation

import java.io.{StringReader, StringWriter}

import javax.xml.transform.stream.{StreamResult, StreamSource}
import javax.xml.transform.{TransformerConfigurationException, TransformerFactory}
import org.xml.sax.SAXParseException


case class SaxonXsltXmlTransformer() extends XsltXmlTransformer {

  def transform(input: String, mapping: String): String = {

    val writer: StringWriter = new StringWriter()
    val resultWriter = new StreamResult(writer)

    val mapSource: StreamSource = new StreamSource(new StringReader(mapping))
    val inputSource: StreamSource = new StreamSource(new StringReader(input))

    try {
      val factory = TransformerFactory.newInstance()
      val transformer = factory.newTransformer(mapSource)
      transformer.transform(inputSource, resultWriter)

      writer.toString
    } catch {
      case e: TransformerConfigurationException => throw InvalidMappingError(e.getLocalizedMessage)
      case e: SAXParseException => throw InvalidMappingError(e.getLocalizedMessage)
    }
  }
}
