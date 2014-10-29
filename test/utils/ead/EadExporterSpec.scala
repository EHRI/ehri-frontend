package utils.ead

import helpers.IntegrationTestRunner
import backend.ApiUser
import java.io.{ByteArrayInputStream, InputStream}
import javax.xml.validation.SchemaFactory
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.transform.stream
import org.xml.sax.SAXParseException

/**
 * @author Mike Bryant (http://github.com/mikesname)
 */
class EadExporterSpec extends IntegrationTestRunner {
  implicit val apiUser: ApiUser = ApiUser(Some("mike"))

  private def resourceInputStream(s: String): InputStream =
    getClass.getClassLoader.getResourceAsStream(s)

  private def stringToInputStream(s: String): InputStream =
    new ByteArrayInputStream(s.getBytes("UTF-8"))

  private def validateXml(src: InputStream, xsd: InputStream): Unit = {
    // http://stackoverflow.com/questions/6815579/validating-xml-against-xsd
    require(xsd != null)
    val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val schema = factory.newSchema(new StreamSource(xsd))
    val validator = schema.newValidator()
    validator.validate(new stream.StreamSource(src))
  }

  "EadExporter" should {
    "export valid EAD" in new ITestApp {
      val ead  = await(EadExporter(testBackend).exportEad("c1", "http://example.com/c1/ead"))
      validateXml(stringToInputStream(ead), resourceInputStream("ead.xsd")) must not(throwA[SAXParseException])
    }
  }
}
