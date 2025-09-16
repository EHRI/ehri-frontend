package eu.ehri.project.xml

import net.sf.saxon.s9api._
import org.slf4j.LoggerFactory
import org.xml.sax.{InputSource, SAXParseException}
import play.api.libs.json._

import java.io.{StringReader, StringWriter}
import javax.inject.Inject
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.{ErrorListener, TransformerConfigurationException, TransformerException}

case class SaxonXsltXmlTransformer @Inject()() extends XsltXmlTransformer {

  private val logger = LoggerFactory.getLogger(SaxonXsltXmlTransformer.getClass)
  // TODO: Cache compiled stylesheet against mapping and parameters?

  def transform(input: String, mapping: String, params: JsObject = Json.obj()): String = {
    if (mapping.trim.isEmpty) input else {
      val writer = new StringWriter()
      val mapSource = new StreamSource(new StringReader(mapping))
      val inputSource = new StreamSource(new StringReader(stripUTF8BOM(input)))

      val processor = new Processor(false)
      val configuration = processor.getUnderlyingConfiguration
      val options = configuration.getParseOptions
      options.setEntityResolver((publicId: String, systemId: String) => {
        logger.warn(s"Skipping entity resolution: '$systemId' '$publicId'")
        new InputSource(new StringReader(""))
      })
      val compiler = processor.newXsltCompiler()
      compiler.setErrorReporter((error: XmlProcessingError) => logger.error(error.getMessage))

      params.fields.foreach {
        case (field, JsString(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (field, JsNumber(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value.bigDecimal))
        case (field, JsBoolean(value)) => compiler.setParameter(new QName(field), new XdmAtomicValue(value))
        case (_, JsNull) => // Ignore value...
        case (field, _) => throw XsltConfigError(s"Parameter key '$field' has an unsupported type, " +
          s"currently only string, number, and boolean can be used")
      }

      try {
        val stylesheet = compiler.compile(mapSource)
        val out = processor.newSerializer(writer)
        out.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "yes")
        out.setOutputProperty(Serializer.Property.INDENT, "yes")

        val transformer = stylesheet.load30()
        transformer.setErrorListener(new ErrorListener {
          override def warning(exception: TransformerException): Unit = logger.warn(exception.getMessage)
          override def error(exception: TransformerException): Unit = logger.error(exception.getMessage)
          override def fatalError(exception: TransformerException): Unit = logger.error(exception.getMessage)
        })

        transformer.transform(inputSource, out)
        writer.toString
      } catch {
        case e: SaxonApiException => throw XsltConfigError(e.getMessage)
        case e: TransformerConfigurationException => throw XsltConfigError(e.getMessage)
        case e: SAXParseException => throw XmlTransformationError(e.getLineNumber, e.getColumnNumber, e.getMessage)
      }
    }
  }
}
