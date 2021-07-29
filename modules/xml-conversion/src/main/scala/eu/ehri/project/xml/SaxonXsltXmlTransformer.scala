package eu.ehri.project.xml

import net.sf.saxon.s9api._
import org.xml.sax.SAXParseException
import play.api.libs.json.{JsBoolean, JsNull, JsNumber, JsObject, JsString, Json}

import java.io.{StringReader, StringWriter}
import javax.inject.Inject
import javax.xml.transform.TransformerConfigurationException
import javax.xml.transform.stream.StreamSource

case class SaxonXsltXmlTransformer @Inject()() extends XsltXmlTransformer {

  // TODO: Cache compiled stylesheet against mapping and parameters?

  def transform(input: String, mapping: String, params: JsObject = Json.obj()): String = {
    if (mapping.trim.isEmpty) input else {
      val writer: StringWriter = new StringWriter()
      val mapSource: StreamSource = new StreamSource(new StringReader(mapping))
      val inputSource: StreamSource = new StreamSource(new StringReader(input))

      val processor = new Processor(false)
      val compiler = processor.newXsltCompiler()

      params.fields.foreach {
        case (field, JsString(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (field, JsNumber(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value.bigDecimal))
        case (field, JsBoolean(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (_, JsNull) => // Ignore value...
        case (field, _) => throw InvalidMappingError(s"Parameter key '$field' has an unsupported type, " +
          s"currently only string, number, and boolean can be used")
      }

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
