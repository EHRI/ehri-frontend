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

    "handle custom functions 2" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString +
          "\n/ead/eadheader/\teadid\t/ead/eadheader/eadid\txtra:language-name-to-code('Ukrainian')"
      val out = transformer.transform(testPayload, map)
      out must contain("ukr")
    }

    "report errors with context" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\teadid\t/ead/eadheader/eadid\tinvalid-func()"
      transformer.transform(testPayload, map) must throwA[InvalidMappingError].like {
        case e => e.getMessage must contain("mapping-error at /ead: err:XPST0017 at /ead/eadheader: Unknown function: invalid-func")
      }
    }

    "handle standard functions in a mapping value" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\tfiledesc\t.\tstring-join(for-each(('a', 'b'), upper-case#1))"
      val out = transformer.transform(testPayload, map)
      out must contain("<filedesc>AB</filedesc>")
    }

    "not resolve external entities in the input" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val map = Source.fromResource("simple-mapping.tsv").mkString
      val input = "<!DOCTYPE ead [<!ENTITY e SYSTEM \"file:///etc/hostname\">]>" +
        "<ead><eadheader><eadid>&e;test-id</eadid></eadheader></ead>"
      val hostname = Source.fromFile("/etc/hostname").mkString.trim
      val out = transformer.transform(input, map)
      out must contain("<eadid>test-id-foo</eadid>")
      out must not(contain(hostname))
    }

    "not allow mapping values to access the system" in {
      def mapWith(expr: String): String = Source.fromResource("simple-mapping.tsv").mkString +
        s"\n/ead/eadheader/\tfiledesc\t.\t$expr"

      // Each of these would otherwise read local files or environment
      // variables, make requests, or run commands.
      Seq(
        "string(file:exists('/etc/hostname'))",
        "proc:system('echo', 'hello')",
        "unparsed-text('file:///etc/hostname')",
        "count(doc('file:///etc/hostname'))",
        "count(http:send-request(<http:request method='get'/>, 'http://localhost/'))",
        "string(Q{java:java.lang.System}getProperty('user.name'))",
        "xquery:eval('1', map{}, map{'permission': 'admin'})",
        "fetch:text('file:///etc/hostname')",
        "function-lookup(QName('http://basex.org/modules/fetch', 'text'), 1)('file:///etc/hostname')",
        "environment-variable('PATH')",
        "string-join(available-environment-variables())",
        "string(parse-xml('<x xmlns:xi=\"http://www.w3.org/2001/XInclude\"><xi:include href=\"file:///etc/hostname\" parse=\"text\"/></x>'))",
      ).map { expr =>
        BaseXXQueryXmlTransformer().transform(testPayload, mapWith(expr)) must throwA[InvalidMappingError].like {
          case e => e.getMessage must beMatching("(?s).*No (admin|create|read) permission.*")
        }
      }
    }
  }
}
