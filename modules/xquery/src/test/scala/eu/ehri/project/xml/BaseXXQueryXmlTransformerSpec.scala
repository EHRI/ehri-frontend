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

    "indent the output" in {
      val transformer = xml.BaseXXQueryXmlTransformer()
      val out = transformer.transform(testPayload, Source.fromResource("simple-mapping.tsv").mkString)
      out must contain("\n  <eadheader>\n    <eadid>")
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

    "handle empty string literals in mapping values" in {
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\tfiledesc\t.\tif (fn:false()) then \"x\" else fn:concat(\"\", \"ok\", \"\")"
      val out = xml.BaseXXQueryXmlTransformer().transform(testPayload, map)
      out must contain("<filedesc>ok</filedesc>")
    }

    "parse markup in mapping values" in {
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\tfiledesc\t.\txtra:parse-markup('<p>A <emph>title</emph></p>')//emph/fn:string()"
      val out = xml.BaseXXQueryXmlTransformer().transform(testPayload, map)
      out must contain("<filedesc>title</filedesc>")
    }

    "parse markup with text and several elements in mapping values" in {
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\tfiledesc\t.\tfn:count(xtra:parse-markup('Intro <p>a</p><p>b</p>')/node())"
      val out = xml.BaseXXQueryXmlTransformer().transform(testPayload, map)
      out must contain("<filedesc>3</filedesc>")
    }

    "read mappings with a byte order mark or untidy header" in {
      val map = Source.fromResource("simple-mapping.tsv").mkString
      val Array(header, rest) = map.split("\n", 2)
      Seq(
        "\uFEFF" + map,
        header + "\t\n" + rest,
      ).map { m =>
        xml.BaseXXQueryXmlTransformer().transform(testPayload, m) must contain("test-id-foo")
      }
    }

    "not load external resources when parsing markup in mapping values" in {
      val secret = java.nio.file.Files.createTempFile("secret", ".txt")
      try {
        java.nio.file.Files.writeString(secret, "SECRET-MARKUP")
        def mapWith(markup: String): String = Source.fromResource("simple-mapping.tsv").mkString +
          s"\n/ead/eadheader/\tfiledesc\t.\tfn:string(xtra:parse-markup('$markup'))"

        // DTDs, and so external entities, are not allowed...
        val entity = s"""<!DOCTYPE x [<!ENTITY e SYSTEM "${secret.toUri}">]><x>&amp;e;</x>"""
        xml.BaseXXQueryXmlTransformer().transform(testPayload, mapWith(entity)) must throwA[InvalidMappingError]

        // ...and XIncludes are not processed
        val xinclude = s"""<x xmlns:xi="http://www.w3.org/2001/XInclude"><xi:include href="${secret.toUri}" parse="text"/></x>"""
        xml.BaseXXQueryXmlTransformer().transform(testPayload, mapWith(xinclude)) must not(contain("SECRET"))
      } finally java.nio.file.Files.delete(secret)
    }

    "not allow parse-xml in mapping values" in {
      val map = Source.fromResource("simple-mapping.tsv").mkString +
        "\n/ead/eadheader/\tfiledesc\t.\tparse-xml('<p/>')"
      xml.BaseXXQueryXmlTransformer().transform(testPayload, map) must throwA[InvalidMappingError].like {
        case e => e.getMessage must contain("No create permission")
      }
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
