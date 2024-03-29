package eu.ehri.project.xml

import org.specs2.mutable.Specification
import play.api.libs.json.Json

import scala.io.Source

class SaxonXsltXmlTransformerSpec extends Specification {

  private val testPayload = """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin

  "XML transformer should" should {
    "transform a simple file" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = Source.fromResource("simple-mapping.xsl").mkString
      val out = transformer.transform(testPayload, map, Json.obj())
      out must contain("http://www.loc.gov/ead")
    }

    "handle parameters" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = Source.fromResource("simple-mapping.xsl").mkString
      val out = transformer.transform(testPayload, map, Json.obj("test-param" -> "example", "test-value" -> "Hello, world"))
      out must contain("example")
      out must contain("Hello, world")
    }
  }
}
