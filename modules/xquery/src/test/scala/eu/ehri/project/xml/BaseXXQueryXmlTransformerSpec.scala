package eu.ehri.project.xml

import eu.ehri.project.xml
import org.specs2.mutable.Specification

import scala.io.Source

class BaseXXQueryXmlTransformerSpec extends Specification {

  private val testPayload = """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin

  "XML transformer should" should {
    "transform a simple file" in {
      val transformer = BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString
      val out = transformer.transform(testPayload, map)
      out must contain("http://www.loc.gov")
    }

    "handle custom functions" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString +
          "\n/ead/eadheader/\teadid\t/ead/eadheader/eadid\txtra:ehri()"
      val out = transformer.transform(testPayload, map)
      out must contain("EHRI_xtra_func")
    }

    "report errors with context" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\teadid\t/ead/eadheader/eadid\tinvalid-func()"
      transformer.transform(testPayload, map) must throwA[InvalidMappingError].like {
        case e => e.getMessage must contain("mapping-error at /ead: err:XPST0017 at /ead/eadheader: Unknown function: fn:invalid-func")
      }
    }
  }
}
