package eu.ehri.project.xml

import org.specs2.mutable.Specification
import play.api.libs.json.Json

import net.sf.saxon.s9api.Processor

import java.io.{StringReader, StringWriter}
import java.nio.file.{Files, Path}
import javax.xml.transform.stream.StreamSource
import scala.io.Source
import scala.util.Try

class SaxonXsltXmlTransformerSpec extends Specification {

  private val testPayload = """<ead>
      |  <eadheader>
      |    <eadid>test-id</eadid>
      |  </eadheader>
      |</ead>
      |""".stripMargin

  private def withTempDir[R](f: Path => R): R = {
    val dir = Files.createTempDirectory("xslt-sandbox")
    try f(dir) finally {
      Files.walk(dir).sorted(java.util.Comparator.reverseOrder()).forEach(p => Files.delete(p))
    }
  }

  // Run a stylesheet with a plain Saxon processor, to show what an attack
  // would do without the sandbox.
  private def unsandboxed(input: String, xsl: String): Try[String] = Try {
    val processor = new Processor(false)
    val writer = new StringWriter()
    val stylesheet = processor.newXsltCompiler().compile(new StreamSource(new StringReader(xsl)))
    stylesheet.load30().transform(new StreamSource(new StringReader(input)), processor.newSerializer(writer))
    writer.toString
  }

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

    "handle BOMs" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = Source.fromResource("simple-mapping.xsl").mkString
      val payload = "\uFEFF" + testPayload
      val out = transformer.transform(payload, map, Json.obj("test-param" -> "example", "test-value" -> "Hello, world"))
      out must contain("example")
      out must contain("Hello, world")
    }

    "handle DTDs" in {
      val transformer = SaxonXsltXmlTransformer()
      val map = Source.fromResource("simple-mapping.xsl").mkString
      val payload = testPayload.replace("<ead>", "<!DOCTYPE ead PUBLIC \"+//ISBN 1-931666-00-8//DTD ead.dtd (Encoded Archival Description (EAD) Version 2002)//EN\" \"ead.dtd\"><ead>")
      val out = transformer.transform(payload, map, Json.obj("test-param" -> "example", "test-value" -> "Hello, world"))
      out must contain("example")
      out must contain("Hello, world")
    }

    "not allow stylesheets to access the system" in withTempDir { dir =>
      // Temporary files standing in for sensitive local files
      def write(name: String, content: String): String = {
        val f = dir.resolve(name)
        Files.writeString(f, content)
        f.toUri.toString
      }
      val xmlUri = write("secret.xml", "<secret>SECRET-XML</secret>")
      val textUri = write("secret.txt", "SECRET-TEXT")
      val jsonUri = write("secret.json", "{\"secret\": \"SECRET-JSON\"}")
      val includeUri = write("include.xsl",
        """<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
          |<xsl:template name="leak">SECRET-INCLUDE</xsl:template></xsl:stylesheet>""".stripMargin)
      val outFile = dir.resolve("written.xml")

      def sheet(body: String, top: String = ""): String =
        s"""<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
           |$top<xsl:template match="/"><out>$body</out></xsl:template></xsl:stylesheet>""".stripMargin

      // A stylesheet that runs another one, which reads a file, with fn:transform
      // and a new (unrestricted) Saxon configuration.
      val inner = s"""<xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
        |<xsl:template match="/"><x><xsl:value-of select="unparsed-text('$textUri')"/></x></xsl:template>
        |</xsl:stylesheet>""".stripMargin
      val transformOptions = "map{'stylesheet-text': string($inner), 'source-node': ., " +
        "'vendor-options': map{QName('http://saxon.sf.net/', 'configuration'): $cfg/*}}"
      val transformVars = s"""<xsl:variable name="inner"><![CDATA[$inner]]></xsl:variable>
        |<xsl:variable name="cfg"><configuration xmlns="http://saxon.sf.net/ns/configuration" edition="HE"/></xsl:variable>""".stripMargin

      val denied = "Access to external resources is not allowed"
      val noResultDocument = "xsl:result-document is disabled when extension functions are disabled"
      val noTransform = "Cannot find a 1-argument function named Q{http://www.w3.org/2005/xpath-functions}transform()"

      // Each of these reads local files or environment variables, or writes
      // files, when not sandboxed. With the sandbox, each must either fail
      // with the given message, or (where None) succeed without the secret.
      val attacks: Seq[(String, String, Option[String])] = Seq(
        ("document", sheet(s"""<xsl:value-of select="document('$xmlUri')"/>"""), Some(denied)),
        ("doc", sheet(s"""<xsl:value-of select="doc('$xmlUri')"/>"""), Some(denied)),
        ("doc-available", sheet(s"""<xsl:value-of select="if (doc-available('$xmlUri')) then 'SECRET-AVAILABLE' else ''"/>"""), None),
        ("unparsed-text", sheet(s"""<xsl:value-of select="unparsed-text('$textUri')"/>"""), Some(denied)),
        ("unparsed-text-lines", sheet(s"""<xsl:value-of select="unparsed-text-lines('$textUri')"/>"""), Some(denied)),
        ("json-doc", sheet(s"""<xsl:value-of select="json-doc('$jsonUri')?secret"/>"""), Some(denied)),
        ("uri-collection", sheet(s"""<xsl:value-of select="if (exists(uri-collection('${dir.toUri}'))) then 'SECRET-COLLECTION' else ''"/>"""), Some(denied)),
        ("environment-variable", sheet("""<xsl:value-of select="if (environment-variable('PATH')) then 'SECRET-ENV' else ''"/>"""), None),
        ("available-environment-variables", sheet("""<xsl:value-of select="if (exists(available-environment-variables())) then 'SECRET-ENV' else ''"/>"""), None),
        ("include", sheet("""<xsl:call-template name="leak"/>""", s"""<xsl:include href="$includeUri"/>"""), Some(denied)),
        ("transform", sheet(s"""$transformVars<xsl:value-of select="transform($transformOptions)?output"/>"""), Some(noTransform)),
        ("transform via function-lookup", sheet(
          s"""$transformVars<xsl:variable name="f" select="function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'transform'), 1)"/>
             |<xsl:value-of select="if (exists($$f)) then $$f($transformOptions)?output else ''"/>""".stripMargin), None),
        ("result-document", sheet(s"""<xsl:result-document href="${outFile.toUri}"><x>SECRET-WRITTEN</x></xsl:result-document>"""), Some(noResultDocument)),
      )

      def leaked(out: Try[String]): Boolean =
        out.toOption.exists(_.contains("SECRET")) || Files.exists(outFile)

      attacks.map { case (name, xsl, expected) =>
        // Control: each attack works without the sandbox...
        Files.deleteIfExists(outFile)
        leaked(unsandboxed(testPayload, xsl)) aka s"$name leaks without sandbox" must beTrue

        // ...but not with it.
        Files.deleteIfExists(outFile)
        val out = Try(SaxonXsltXmlTransformer().transform(testPayload, xsl, Json.obj()))
        leaked(out) aka s"$name leaks" must beFalse
        expected match {
          case Some(msg) => out.failed.map(_.getMessage).getOrElse("") aka s"$name error" must contain(msg)
          case None => out.isSuccess aka s"$name succeeds" must beTrue
        }
      }
    }

    "not resolve external entities in the input" in withTempDir { dir =>
      val secret = dir.resolve("secret.txt")
      Files.writeString(secret, "SECRET-ENTITY")
      val payload = s"""<!DOCTYPE ead [<!ENTITY e SYSTEM "${secret.toUri}">]><ead><eadheader><eadid>&e;test-id</eadid></eadheader></ead>"""
      val map = Source.fromResource("simple-mapping.xsl").mkString

      unsandboxed(payload, map).toOption.exists(_.contains("SECRET")) must beTrue
      val out = SaxonXsltXmlTransformer().transform(payload, map, Json.obj())
      out must not(contain("SECRET"))
    }
  }
}
