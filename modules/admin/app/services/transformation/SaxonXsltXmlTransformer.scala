package services.transformation

import java.io.{StringReader, StringWriter}

import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.stream.StreamSource
import net.sf.saxon.s9api.{Processor, SaxonApiException, Serializer}
import org.xml.sax.SAXParseException


case class SaxonXsltXmlTransformer() extends XsltXmlTransformer {

  def transform(input: String, mapping: String): String = {
    if (mapping.trim.isEmpty) input else {
      val writer: StringWriter = new StringWriter()
      val mapSource: StreamSource = new StreamSource(new StringReader(mapping))
      val inputSource: StreamSource = new StreamSource(new StringReader(input))

      val processor = new Processor(false)
      val compiler = processor.newXsltCompiler()

      try {
        val stylesheet = compiler.compile(mapSource)
        val out = processor.newSerializer(writer)
        out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes")
        out.setOutputProperty(Serializer.Property.INDENT, "yes")

        val transformer = stylesheet.load30()
        transformer.transform(inputSource, out)
        writer.toString
      } catch {
        case e: SaxonApiException => throw InvalidMappingError(e.getMessage)
        case e: TransformerConfigurationException => throw InvalidMappingError(e.getMessage)
        case e: SAXParseException => throw XmlTransformationError(e.getLineNumber, e.getColumnNumber, e.getMessage)
      }
    }
  }
}
