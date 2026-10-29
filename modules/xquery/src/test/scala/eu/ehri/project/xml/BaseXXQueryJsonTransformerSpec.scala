package eu.ehri.project.xml

import org.specs2.mutable.Specification

import scala.io.Source

class BaseXXQueryJsonTransformerSpec extends Specification {

  private val testPayload =
    """{
      |  "id": "test-001",
      |  "meta": { "title": "Test Collection", "lang": "English" },
      |  "records": [
      |    { "ref": "a", "date": "1943-1945" },
      |    { "ref": "b", "date": "1950" }
      |  ]
      |}
      |""".stripMargin

  "JSON transformer should" should {
    "transform a simple document" in {
      val transformer = BaseXXQueryJsonTransformer()
      val map = Source.fromResource("simple-json-mapping.tsv").mkString
      val out = transformer.transform(testPayload, map, Map.empty)
      out must contain("test-001")
      out must contain("Test Collection")
    }

    "invoke custom xtra functions" in {
      val transformer = BaseXXQueryJsonTransformer()
      val map = Source.fromResource("simple-json-mapping.tsv").mkString
      val out = transformer.transform(testPayload, map, Map.empty)
      // language "English" mapped to code "eng" by xtra:language-name-to-code
      out must contain("eng")
    }

    "build nested arrays with normalised values" in {
      val transformer = BaseXXQueryJsonTransformer()
      val map = Source.fromResource("simple-json-mapping.tsv").mkString
      val out = transformer.transform(testPayload, map, Map.empty)
      // date interval "1943-1945" normalised to "1943/1945"
      out must contain("1943/1945")
      out must contain("\"ref\"")
    }

    "transform an array of objects into an array of different objects" in {
      val arrayPayload =
        """[
          |  { "ref": "a", "title": "First", "lang": "English" },
          |  { "ref": "b", "title": "Second", "lang": "German" }
          |]
          |""".stripMargin
      val transformer = BaseXXQueryJsonTransformer()
      val map = Source.fromResource("array-json-mapping.tsv").mkString
      val out = transformer.transform(arrayPayload, map, Map.empty)
      // Output is a bare JSON array (starts with '['), not an object wrapper
      out.trim must startWith("[")
      out must contain("\"identifier\": \"a\"")
      out must contain("\"name\": \"Second\"")
      out must contain("\"language\": \"deu\"")
      out must not(contain("\"ref\""))
    }

    "report errors with context" in {
      val transformer = BaseXXQueryJsonTransformer()
      val map = Source.fromResource("simple-json-mapping.tsv").mkString +
        "\n/\tbad\tvalue\t.\tinvalid-func()"
      transformer.transform(testPayload, map, Map.empty) must throwA[InvalidMappingError].like {
        case e => e.getMessage must contain("at /bad") and(e.getMessage must contain("Unknown function: invalid-func"))
      }
    }

    "keep numbers exact, without exponents" in {
      val payload = """{"id": 12345678, "big": 12345678901234567890, "dec": 1.5}"""
      val map = "target-path\ttarget-node\ttype\tsource-node\tvalue\n" +
        "/\tidentifier\tvalue\t.\tfn:string(?id)\n/\tbig\tvalue\t.\t?big\n/\tdec\tvalue\t.\t?dec\n"
      val out = BaseXXQueryJsonTransformer().transform(payload, map, Map.empty)
      out must contain("\"identifier\": \"12345678\"")
      out must contain("\"big\": 12345678901234567890")
      out must contain("\"dec\": 1.5")
    }

    "keep false and zero values, but drop empty ones" in {
      val payload = """{"public": false, "count": 0, "blank": "", "none": {}}"""
      val map = "target-path\ttarget-node\ttype\tsource-node\tvalue\n" +
        "/\tpublic\tvalue\t.\t?public\n/\tcount\tvalue\t.\t?count\n" +
        "/\tblank\tvalue\t.\t?blank\n/\tnone\tvalue\t.\t?none\n"
      val out = BaseXXQueryJsonTransformer().transform(payload, map, Map.empty)
      out must contain("\"public\": false")
      out must contain("\"count\": 0")
      out must not(contain("blank"))
      out must not(contain("none"))
    }

    "handle empty string literals in mapping values" in {
      val map = "target-path\ttarget-node\ttype\tsource-node\tvalue\n/\tflag\tvalue\t.\t" +
        "if (fn:false()) then \"x\" else fn:concat(\"\", \"ok\")"
      BaseXXQueryJsonTransformer().transform(testPayload, map, Map.empty) must contain("\"flag\": \"ok\"")
    }

    "parse markup in mapping values" in {
      val map = "target-path\ttarget-node\ttype\tsource-node\tvalue\n/\tparsed\tvalue\t.\t" +
        "xtra:parse-markup('<p>A <b>bold</b></p>')//b/fn:string()"
      BaseXXQueryJsonTransformer().transform(testPayload, map, Map.empty) must contain("\"parsed\": \"bold\"")
    }

    "not allow mapping values to access the system" in {
      // Each of these would otherwise read local files or environment
      // variables, make requests, or run commands.
      Seq(
        "string(file:exists('/etc/hostname'))",
        "proc:system('echo', 'hello')",
        "unparsed-text('file:///etc/hostname')",
        "fetch:text('file:///etc/hostname')",
        "environment-variable('PATH')",
        "string(Q{java:java.lang.System}getProperty('user.name'))",
      ).map { expr =>
        val map = "target-path\ttarget-node\ttype\tsource-node\tvalue\n/\tleak\tvalue\t.\t" + expr
        BaseXXQueryJsonTransformer().transform(testPayload, map, Map.empty) must
          throwA[InvalidMappingError].like { case e => e.getMessage must beMatching("(?s).*No (admin|create|read) permission.*") }
      }
    }
  }
}
